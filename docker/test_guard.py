#!/usr/bin/env python3
"""Unit tests for guard policy; these never start Docker or execute repository code."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("guard", Path(__file__).with_name("guard.py"))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class GuardTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.addCleanup(self.temporary.cleanup)

    def payload(self, files):
        return json.dumps({path: base64.b64encode(content).decode() for path, content in files.items()}).encode()

    def test_snapshot_rejects_traversal_git_windows_controls_empty_segments(self):
        for name in ("../evil", "/root", "a/../../escape", "a/.git/config", ".GIT/HEAD", "a\\b", "a:b", "a//b", "a/./b", "a\x00b", "a/", "\x85"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                guard.decode_snapshot(self.payload({name: b"x"}))

    def test_snapshot_rejects_duplicates_bad_base64_file_directory_conflicts(self):
        for payload in (b'{"a":"eA==","a":"eQ=="}', b'{"a":"!!!"}', b'{"a":"eA==","a/b":"eA=="}', b'[]', b'{}'):
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                guard.decode_snapshot(payload)

    def test_snapshot_bounds_file_size_and_depth(self):
        with self.assertRaises(ValueError):
            guard.decode_snapshot(self.payload({"x": b"x" * (guard.MAX_FILE + 1)}))
        with self.assertRaises(ValueError):
            guard.decode_snapshot(self.payload({"/".join(["a"] * 33): b"x"}))

    def test_import_preserves_bytes_and_rejects_nonempty_destination(self):
        workspace, cache = self.root / "workspace", self.root / "cache"
        workspace.mkdir(); cache.mkdir()
        files = {"pom.xml": b"<project/>", "src/A.java": b"class A {}", "blob": bytes(range(256))}
        result = guard.import_snapshot(self.payload(files), workspace, cache, self.root / "absent")
        self.assertEqual(3, result["files"])
        for path, content in files.items():
            self.assertEqual(content, (workspace / path).read_bytes())
        with self.assertRaises(ValueError):
            guard.import_snapshot(self.payload({"x": b"x"}), workspace, cache, self.root / "absent")

    def report(self, name="target/surefire-reports/TEST-example.xml", xml=b'<testsuite tests="3" failures="1" errors="0" skipped="1"/>'):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(xml)
        return path

    def test_junit_counts_and_clean_stale_reports(self):
        self.report()
        self.report("module/build/test-results/test/TEST-other.xml", b'<testsuites><testsuite tests="2" failures="0" errors="1" skipped="0"/></testsuites>')
        self.assertEqual({"tests": 5, "failures": 2, "skipped": 1, "reports": 2}, guard.reports(self.root))
        self.assertEqual({"removed": 2}, guard.clean_reports(self.root))
        self.assertEqual(0, guard.reports(self.root)["tests"])

    def test_nested_suites_do_not_double_count_summary(self):
        counts = guard.parse_report(b'<testsuite tests="3"><testsuite tests="3" failures="0"/></testsuite>')
        self.assertEqual(3, counts["tests"])

    def test_rejects_dtd_utf16_invalid_counts_and_unknown_root(self):
        for xml in (b'<!DOCTYPE x [<!ENTITY x SYSTEM "file:///etc/passwd">]><testsuite/>',
                    '<testsuite tests="1"/>'.encode("utf-16"), b'<testsuite tests="-1"/>',
                    b'<testsuite tests="1" errors="2"/>', b'<testsuite tests="1000001"/>', b'<unknown/>'):
            with self.subTest(xml=xml[:50]), self.assertRaises(ValueError):
                guard.parse_report(xml)

    def test_rejects_malformed_xml(self):
        with self.assertRaises(ET.ParseError):
            guard.parse_report(b"<testsuite>")

    def test_reports_never_follow_file_symlinks_or_hardlinks(self):
        external = self.root / "secret"
        external.write_bytes(b'<testsuite tests="4"/>')
        report = self.root / "target/surefire-reports/TEST-x.xml"
        report.parent.mkdir(parents=True)
        report.symlink_to(external)
        with self.assertRaises(ValueError):
            guard.reports(self.root)
        report.unlink(); os.link(external, report)
        with self.assertRaises(ValueError):
            guard.reports(self.root)

    def test_report_directory_symlink_is_not_followed(self):
        external = self.root / "outside"
        external.mkdir(); (external / "TEST.xml").write_bytes(b'<testsuite tests="5"/>')
        (self.root / "target").mkdir(); (self.root / "target/surefire-reports").symlink_to(external, target_is_directory=True)
        self.assertEqual(0, guard.reports(self.root)["tests"])

    def test_fifo_and_large_report_are_rejected_without_blocking(self):
        path = self.root / "target/surefire-reports/TEST-x.xml"
        path.parent.mkdir(parents=True); os.mkfifo(path)
        with self.assertRaises(ValueError):
            guard.reports(self.root)
        path.unlink(); path.write_bytes(b"x" * (1024 * 1024 + 1))
        with self.assertRaises(ValueError):
            guard.reports(self.root)

    def limit_fixture(self):
        cgroup, proc, sysfs = self.root / "cgroup", self.root / "proc", self.root / "sys"
        cgroup.mkdir(); (proc / "self").mkdir(parents=True); (sysfs / "class/net/lo").mkdir(parents=True)
        for name, content in {"memory.max": str(1536 * 1024 * 1024), "memory.swap.max": "0", "pids.max": "128", "cpu.max": "100000 100000"}.items():
            (cgroup / name).write_text(content)
        (proc / "self/status").write_text("Uid:\t10000 10000 10000 10000\nNoNewPrivs:\t1\nSeccomp:\t2\nCapEff:\t00000000\nCapPrm:\t00000000\nCapBnd:\t00000000\n")
        (proc / "self/mountinfo").write_text("1 0 0:1 / / ro - overlay overlay ro\n" + "".join(f"{i} 1 0:{i} / {path} rw,nosuid,nodev,noexec - tmpfs tmpfs rw\n" for i, path in enumerate(("/workspace", "/cache", "/state", "/tmp"), 2)))
        return cgroup, proc, sysfs

    def test_limits_accept_fully_enforced_fixture(self):
        result = guard.check_limits(*self.limit_fixture())
        self.assertEqual("none", result["network"])
        self.assertEqual(128, result["pids"])

    def test_limits_reject_missing_or_unbounded_controllers(self):
        cgroup, proc, sysfs = self.limit_fixture()
        for name, bad in (("memory.max", "max"), ("memory.swap.max", "1"), ("pids.max", "129"), ("cpu.max", "200000 100000")):
            original = (cgroup / name).read_text(); (cgroup / name).write_text(bad)
            with self.subTest(name=name), self.assertRaises(ValueError):
                guard.check_limits(cgroup, proc, sysfs)
            (cgroup / name).write_text(original)
        (cgroup / "cpu.max").unlink()
        with self.assertRaises(OSError):
            guard.check_limits(cgroup, proc, sysfs)

    def test_limits_reject_network_capabilities_and_writable_root(self):
        cgroup, proc, sysfs = self.limit_fixture()
        (sysfs / "class/net/eth0").mkdir()
        with self.assertRaises(ValueError):
            guard.check_limits(cgroup, proc, sysfs)
        (sysfs / "class/net/eth0").rmdir()
        status = proc / "self/status"; original = status.read_text()
        status.write_text(original.replace("CapEff:\t00000000", "CapEff:\t00000001"))
        with self.assertRaises(ValueError):
            guard.check_limits(cgroup, proc, sysfs)
        status.write_text(original)
        mounts = proc / "self/mountinfo"; mounts.write_text(mounts.read_text().replace("/ / ro", "/ / rw"))
        with self.assertRaises(ValueError):
            guard.check_limits(cgroup, proc, sysfs)

    def test_build_process_detection_excludes_self_helper_and_zombies(self):
        for pid, uid, state in ((1, 10000, "S"), (2, 10001, "S"), (3, 10001, "Z"), (4, 10001, "S")):
            path = self.root / str(pid); path.mkdir()
            (path / "status").write_text(f"Uid:\t{uid} {uid} {uid} {uid}\nState:\t{state} (state)\n")
        self.assertEqual([2], guard.build_processes(self.root, own_pid=4))

    def test_build_commands_are_fixed_offline_and_do_not_execute_repository_wrappers(self):
        for operation in ("maven-build", "maven-test", "gradle-build", "gradle-test"):
            command = guard.build_command(operation)
            self.assertEqual(["/usr/bin/timeout", "--signal=TERM", "--kill-after=5s", "180s"], command[:4])
            self.assertIn("--offline", command)
            self.assertNotIn("sh", command)
            self.assertNotIn("bash", command)
            self.assertNotIn("./mvnw", command)
            self.assertNotIn("./gradlew", command)
        self.assertIn("test", guard.build_command("maven-test"))
        self.assertIn("--rerun-tasks", guard.build_command("gradle-test"))
        for operation in ("maven-test;curl evil", "shell", "maven", "gradle-build --online"):
            with self.subTest(operation=operation), self.assertRaises(ValueError):
                guard.build_command(operation)

    def test_limits_reject_missing_seccomp_privileges_wrong_uid_and_executable_tmpfs(self):
        cgroup, proc, sysfs = self.limit_fixture()
        status = proc / "self/status"
        original = status.read_text()
        for bad in (original.replace("Seccomp:\t2", "Seccomp:\t0"),
                    original.replace("NoNewPrivs:\t1", "NoNewPrivs:\t0"),
                    original.replace("10000 10000 10000 10000", "0 0 0 0")):
            status.write_text(bad)
            with self.subTest(status=bad), self.assertRaises(ValueError):
                guard.check_limits(cgroup, proc, sysfs)
        status.write_text(original)
        mounts = proc / "self/mountinfo"
        mounts.write_text(mounts.read_text().replace("rw,nosuid,nodev,noexec", "rw,nosuid,nodev", 1))
        with self.assertRaises(ValueError):
            guard.check_limits(cgroup, proc, sysfs)


if __name__ == "__main__":
    unittest.main(verbosity=2)
