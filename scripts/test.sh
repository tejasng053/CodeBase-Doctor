#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$project_dir/scripts/test_dev.py"
python3 "$project_dir/docker/test_guard.py"
(cd "$project_dir/backend" && mvn -Dmaven.repo.local="$project_dir/.runtime/m2" test)
(cd "$project_dir/frontend" && npm run build)
