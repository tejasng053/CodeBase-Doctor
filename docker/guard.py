#!/usr/bin/python3
"""Trusted, immutable helper. Run with python -I; never import modules from repository paths.

The helper UID (10000) owns imports. Build UID 10001 can change the repository, but
cannot change this program or read /state. Report collection follows quiescence.
"""
import base64
import binascii
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import signal
import stat
import sys
import time
import xml.etree.ElementTree as ET

WORKSPACE = Path("/workspace")
CACHE = Path("/cache")
MAX_FILES = 5000
MAX_FILE = 2 * 1024 * 1024
MAX_TOTAL = 24 * 1024 * 1024
MAX_INPUT = 36 * 1024 * 1024
MAX_REPORT_BYTES = 8 * 1024 * 1024


def source_path(value):
    if not isinstance(value, str) or not value.strip() or len(value) > 512:
        raise ValueError("Invalid source path")
    if value.startswith("/") or "\\" in value or ":" in value or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value):
        raise ValueError("Unsafe source path")
    parts = value.split("/")
    if len(parts) > 32 or any(p in ("", ".", "..") or p.lower() == ".git" for p in parts):
        raise ValueError("Unsafe source path")
    return parts


def unique_object(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError("Duplicate source path")
        result[key] = value
    return result


def decode_snapshot(data):
    if len(data) > MAX_INPUT:
        raise ValueError("Snapshot input exceeds limit")
    value = json.loads(data, object_pairs_hook=unique_object)
    if not isinstance(value, dict) or not 0 < len(value) <= MAX_FILES:
        raise ValueError("Snapshot must contain 1–5000 files")
    files = {}
    total = 0
    for name, encoded in value.items():
        source_path(name)
        if not isinstance(encoded, str) or len(encoded) > ((MAX_FILE + 2) // 3) * 4:
            raise ValueError("Invalid file payload")
        try:
            content = base64.b64decode(encoded, validate=True)
        except (binascii.Error, ValueError) as error:
            raise ValueError("Invalid base64 payload") from error
        if len(content) > MAX_FILE:
            raise ValueError("File exceeds 2 MiB")
        total += len(content)
        if total > MAX_TOTAL:
            raise ValueError("Snapshot exceeds 24 MiB")
        files[name] = content
    for name in files:
        parent = PurePosixPath(name).parent
        while str(parent) != ".":
            if str(parent) in files:
                raise ValueError("File/directory path collision")
            parent = parent.parent
    return files


def import_snapshot(data, workspace=WORKSPACE, cache=CACHE, seed=Path("/opt/doctor/m2")):
    files = decode_snapshot(data)
    if workspace.is_symlink() or any(workspace.iterdir()):
        raise ValueError("Workspace must be an empty directory")
    old_umask = os.umask(0o007)
    try:
        for name, content in sorted(files.items()):
            destination = workspace.joinpath(*source_path(name))
            destination.parent.mkdir(mode=0o770, parents=True, exist_ok=True)
            # There are no build processes until import finishes. O_NOFOLLOW still guards final components.
            fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o660)
            with os.fdopen(fd, "wb") as stream:
                stream.write(content)
        for name in ("home", "gradle", "m2"):
            (cache / name).mkdir(mode=0o770, exist_ok=True)
        if seed.exists():
            shutil.copytree(seed, cache / "m2", dirs_exist_ok=True, symlinks=False)
            for directory, children, filenames in os.walk(cache / "m2"):
                os.chmod(directory, 0o770)
                for filename in filenames:
                    os.chmod(Path(directory) / filename, 0o660)
    finally:
        os.umask(old_umask)
    return {"files": len(files), "bytes": sum(map(len, files.values()))}


def bounded_int_file(path, upper, allow_zero=False):
    value = path.read_text().strip()
    if not value.isdecimal():
        raise ValueError(f"Unbounded resource: {path.name}")
    count = int(value)
    if count > upper or count < (0 if allow_zero else 1):
        raise ValueError(f"Resource limit out of policy: {path.name}")
    return count


def check_limits(cgroup=Path("/sys/fs/cgroup"), proc=Path("/proc"), sysfs=Path("/sys")):
    memory = bounded_int_file(cgroup / "memory.max", 1536 * 1024 * 1024)
    swap = bounded_int_file(cgroup / "memory.swap.max", 0, allow_zero=True)
    pids = bounded_int_file(cgroup / "pids.max", 128)
    cpu = (cgroup / "cpu.max").read_text().split()
    if len(cpu) != 2 or not all(x.isdecimal() for x in cpu) or not 0 < int(cpu[0]) <= int(cpu[1]):
        raise ValueError("CPU quota is not enforced at at most one CPU")
    status = dict(line.split(":", 1) for line in (proc / "self/status").read_text().splitlines() if ":" in line)
    if status.get("NoNewPrivs", "").strip() != "1" or status.get("Seccomp", "").strip() != "2":
        raise ValueError("No-new-privileges/seccomp are not active")
    if any(int(status.get(field, "1").strip(), 16) != 0 for field in ("CapEff", "CapPrm", "CapBnd")):
        raise ValueError("Linux capabilities were not all dropped")
    if not all(int(uid) == 10000 for uid in status.get("Uid", "").split()) or len(status.get("Uid", "").split()) != 4:
        raise ValueError("Guard must use the unprivileged helper UID")
    mounts = {}
    for line in (proc / "self/mountinfo").read_text().splitlines():
        fields = line.split(); divider = fields.index("-")
        mounts[fields[4]] = (set(fields[5].split(",")), fields[divider + 1])
    if "/" not in mounts or "ro" not in mounts["/"][0]:
        raise ValueError("Root filesystem is not read-only")
    for path in ("/workspace", "/cache", "/state", "/tmp"):
        options, filesystem = mounts.get(path, (set(), ""))
        if filesystem != "tmpfs" or not {"rw", "nosuid", "nodev", "noexec"} <= options:
            raise ValueError(f"Unsafe writable filesystem: {path}")
    interfaces = {path.name for path in (sysfs / "class/net").iterdir()}
    if interfaces - {"lo"}:
        raise ValueError("Network interfaces other than loopback are present")
    return {"memoryBytes": memory, "swapBytes": swap, "pids": pids, "cpuQuota": int(cpu[0]), "cpuPeriod": int(cpu[1]), "seccomp": True, "network": "none", "rootfs": "read-only"}


def build_processes(proc=Path("/proc"), own_pid=None):
    own_pid = os.getpid() if own_pid is None else own_pid
    processes = []
    for entry in proc.iterdir():
        if not entry.name.isdecimal() or int(entry.name) == own_pid:
            continue
        try:
            text = (entry / "status").read_text()
            values = dict(line.split(":", 1) for line in text.splitlines() if ":" in line)
            # Zombies cannot execute or fork; Docker's PID namespace is removed after report collection.
            if values.get("State", "").strip().startswith("Z"):
                continue
            if 10001 in [int(value) for value in values.get("Uid", "").split()]:
                processes.append(int(entry.name))
        except (FileNotFoundError, ProcessLookupError):
            pass
    return processes


def quiesce():
    if os.getuid() != 10001:
        raise ValueError("Quiesce must run as build UID")
    deadline = time.monotonic() + 6
    quiet = 0
    while time.monotonic() < deadline:
        remaining = build_processes()
        if not remaining:
            quiet += 1
            if quiet >= 3:
                return {"quiesced": True}
        else:
            quiet = 0
            for pid in remaining:
                try:
                    os.kill(pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
        time.sleep(0.05)
    raise ValueError("Build processes could not be stopped")


def report_paths(workspace=WORKSPACE):
    """Never follow source-produced links; bounded walk after every build process is stopped."""
    seen = 0
    for directory, children, files in os.walk(workspace, followlinks=False):
        seen += len(children) + len(files)
        if seen > 30000:
            raise ValueError("Generated file count exceeds report scan limit")
        for name in list(children):
            path = Path(directory) / name
            if path.is_symlink():
                children.remove(name)
        for name in files:
            path = Path(directory) / name
            relative = path.relative_to(workspace)
            parts = relative.parts
            is_maven = "surefire-reports" in parts or "failsafe-reports" in parts
            is_gradle = "test-results" in parts
            if (is_maven or is_gradle) and name.endswith(".xml"):
                metadata = path.lstat()
                if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
                    raise ValueError("Test report is not a regular, unlinked file")
                yield path


def safe_read(path, maximum):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as stream:
        metadata = os.fstat(stream.fileno())
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1 or metadata.st_size > maximum:
            raise ValueError("Test report type or size is invalid")
        data = stream.read(maximum + 1)
        if len(data) > maximum:
            raise ValueError("Test report exceeds size limit")
        return data


def parse_report(data):
    # UTF-16/32 XML could hide the ASCII DTD tokens from a simple byte search. Accept only UTF-8 XML.
    if b"\x00" in data or re.search(br"<!\s*(DOCTYPE|ENTITY)", data, flags=re.I):
        raise ValueError("DTD, entities and non-UTF8 test reports are forbidden")
    root = ET.fromstring(data.decode("utf-8-sig"))
    if root.tag not in ("testsuite", "testsuites"):
        raise ValueError("Unknown JUnit XML root")
    suites = [node for node in root.iter("testsuite") if not list(node.iterfind("testsuite"))]
    if not suites:
        return {"tests": 0, "failures": 0, "skipped": 0}
    totals = {"tests": 0, "failures": 0, "skipped": 0}
    for suite in suites:
        def number(name):
            value = suite.get(name, "0")
            if not value.isdecimal() or int(value) > 1_000_000:
                raise ValueError("Invalid JUnit count")
            return int(value)
        tests, failures, skipped = number("tests"), number("failures") + number("errors"), number("skipped")
        if failures + skipped > tests:
            raise ValueError("Inconsistent JUnit counts")
        for key, value in (("tests", tests), ("failures", failures), ("skipped", skipped)):
            totals[key] += value
            if totals[key] > 1_000_000:
                raise ValueError("JUnit count exceeds limit")
    return totals


def reports(workspace=WORKSPACE):
    totals = {"tests": 0, "failures": 0, "skipped": 0, "reports": 0}
    byte_count = 0
    for path in report_paths(workspace):
        data = safe_read(path, 1024 * 1024)
        byte_count += len(data)
        totals["reports"] += 1
        if byte_count > MAX_REPORT_BYTES or totals["reports"] > 2000:
            raise ValueError("Test report collection exceeds limit")
        parsed = parse_report(data)
        for key, value in parsed.items():
            totals[key] += value
            if totals[key] > 1_000_000:
                raise ValueError("JUnit count exceeds limit")
    return totals


def clean_reports(workspace=WORKSPACE):
    count = 0
    for path in report_paths(workspace):
        path.unlink()
        count += 1
    return {"removed": count}


def build_command(operation):
    if operation not in ("maven-build", "maven-test", "gradle-build", "gradle-test"):
        raise ValueError("Unsupported build command")
    if operation.startswith("maven-"):
        command = ["/usr/share/maven/bin/mvn", "--offline", "--batch-mode", "--no-transfer-progress", "-Dmaven.repo.local=/cache/m2", "-Dstyle.color=never"]
        command += ["test"] if operation.endswith("-test") else ["-DskipTests", "package"]
    else:
        command = ["/opt/gradle/bin/gradle", "--offline", "--no-daemon", "--console=plain", "--max-workers=1", "-Dorg.gradle.vfs.watch=false"]
        command += ["test", "--rerun-tasks"] if operation.endswith("-test") else ["assemble", "-x", "test"]
    return ["/usr/bin/timeout", "--signal=TERM", "--kill-after=5s", "180s"] + command


def launch_build(operation):
    if os.getuid() != 10001:
        raise ValueError("Build must run as unprivileged build UID")
    command = build_command(operation)
    os.umask(0o007)
    environment = {"PATH": "/opt/java/openjdk/bin:/usr/share/maven/bin:/opt/gradle/bin:/usr/bin:/bin",
                   "HOME": "/cache/home", "LANG": "C.UTF-8", "JAVA_HOME": "/opt/java/openjdk",
                   "GRADLE_USER_HOME": "/cache/gradle", "JAVA_TOOL_OPTIONS": "-Xmx768m -XX:ActiveProcessorCount=1",
                   "MAVEN_OPTS": "-Djansi.force=false -Djava.io.tmpdir=/tmp"}
    os.execve(command[0], command, environment)


def main():
    if len(sys.argv) != 2:
        raise ValueError("One fixed operation is required")
    operation = sys.argv[1]
    if operation in ("maven-build", "maven-test", "gradle-build", "gradle-test"):
        launch_build(operation)
        return
    if operation == "quiesce":
        result = quiesce()
    else:
        if os.getuid() != 10000:
            raise ValueError("Guard must run as helper UID")
        if operation == "limits":
            result = check_limits()
        elif operation == "import":
            result = import_snapshot(sys.stdin.buffer.read(MAX_INPUT + 1))
        elif operation in ("reports", "clean-reports"):
            if build_processes():
                raise ValueError("Build processes remain active; refusing to read output")
            result = reports() if operation == "reports" else clean_reports()
        else:
            raise ValueError("Unknown guard operation")
    print(json.dumps(result, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        print("Sandbox guard rejected operation: " + str(error)[:1500], file=sys.stderr)
        sys.exit(1)
