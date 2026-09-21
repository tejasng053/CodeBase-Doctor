#!/usr/bin/env bash
# Read-only host diagnostics. Does not install Docker, alter permissions, start daemons, or build images.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
image_name="${DOCTOR_SANDBOX_IMAGE:-codebase-doctor-sandbox:local}"
if ! command -v docker >/dev/null 2>&1; then
  echo 'BLOCKED: Docker CLI is not installed. Static analysis and reports still work.'
  exit 1
fi
if ! engine_json="$(timeout 10s docker info --format '{{json .}}' 2>/dev/null)"; then
  echo 'BLOCKED: Docker daemon is inaccessible. No repository commands will run.'
  echo 'Configure a rootless Docker context yourself using https://docs.docker.com/engine/security/rootless/ .'
  echo 'Do not grant access to the root-owned system Docker socket or use sudo for this application.'
  exit 1
fi
printf '%s' "$engine_json" | python3 -c '
import json,sys
value=json.load(sys.stdin)
security=" ".join(value.get("SecurityOptions", []))
checks={"rootless": "rootless" in security, "seccomp": "seccomp" in security,
        "cgroup v2": str(value.get("CgroupVersion")) == "2", "systemd cgroup driver": value.get("CgroupDriver") == "systemd"}
for name,good in checks.items(): print(("PASS: " if good else "BLOCKED: ")+name)
if not all(checks.values()): sys.exit(1)
'
if ! image_json="$(timeout 10s docker image inspect "$image_name" --format '{{json .}}' 2>/dev/null)"; then
  echo 'BLOCKED: Trusted sandbox image is missing. Review docker/sandbox.Dockerfile, then build from the project root:'
  printf '  cd %q\n' "$project_dir"
  printf '  docker build -f docker/sandbox.Dockerfile -t %q .\n' "$image_name"
  exit 1
fi
printf '%s' "$image_json" | python3 -c '
import json,sys
value=json.load(sys.stdin); config=value.get("Config") or {}
if config.get("Volumes") or (config.get("Labels") or {}).get("dev.codebasedoctor.guard-version") != "1":
    print("BLOCKED: Image must be the trusted guard-v1 image and declare no volumes."); sys.exit(1)
print("PASS: Trusted image metadata; image "+value.get("Id", "unknown"))
'
echo 'Prerequisites pass. This does not prove runtime isolation: every verification creates a fresh container and checks actual limits before source import.'
echo 'The sandbox has no network. Repositories requiring dependencies outside the reviewed baked cache will fail offline.'
