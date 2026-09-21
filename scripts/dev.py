#!/usr/bin/env python3
"""Run the trusted app services; .env is parsed as data, never shell code."""
from pathlib import Path
import os
import signal
import subprocess
import time
import argparse
import shutil

ALLOWED_KEYS = {'DOCTOR_API_TOKEN', 'GROQ_API_KEY', 'GROQ_MODEL', 'GITHUB_TOKEN', 'DOCTOR_SANDBOX_IMAGE', 'DOCTOR_DATA_DIR'}


def read_config(path):
    values = {}
    for line in path.read_text().splitlines():
        if '=' not in line or line.lstrip().startswith('#'):
            continue
        key, value = line.split('=', 1)
        if key.strip() in ALLOWED_KEYS:
            values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def stop_group(process):
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    # The parent may have exited while a service child remains.
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--production", action="store_true", help="Serve the prebuilt frontend; backend remains the local Maven service.")
    options = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    env = os.environ.copy()
    env.update(read_config(root / '.env'))
    env['NEXT_TELEMETRY_DISABLED'] = '1'
    cache = root / '.runtime' / 'm2'
    cache.mkdir(parents=True, exist_ok=True)
    processes = []
    def interrupt(*_):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, interrupt)
    try:
        processes.append(subprocess.Popen(['mvn', '-Dmaven.repo.local=' + str(cache), 'spring-boot:run'], cwd=root / 'backend', env=env, start_new_session=True))
        if options.production:
            built = root / 'frontend' / '.next' / 'standalone'
            if not (built / 'server.js').is_file():
                raise RuntimeError('Run npm run build in frontend before using --production.')
            shutil.copytree(root / 'frontend' / '.next' / 'static', built / '.next' / 'static', dirs_exist_ok=True)
            shutil.copytree(root / 'frontend' / 'public', built / 'public', dirs_exist_ok=True)
            processes.append(subprocess.Popen(['node', str(built / 'server.js')], cwd=root / 'frontend', env=env | {'HOSTNAME': '127.0.0.1', 'PORT': '3000'}, start_new_session=True))
        else:
            processes.append(subprocess.Popen(['npm', 'run', 'dev'], cwd=root / 'frontend', env=env, start_new_session=True))
        while all(process.poll() is None for process in processes):
            time.sleep(0.2)
        return next((p.returncode for p in processes if p.returncode is not None), 1)
    except KeyboardInterrupt:
        return 0
    finally:
        for process in reversed(processes):
            stop_group(process)


if __name__ == '__main__':
    raise SystemExit(main())
