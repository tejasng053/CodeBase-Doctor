#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$project_dir" <<'PY'
from pathlib import Path
import os, secrets, sys
root=Path(sys.argv[1])
env=root/'.env'
os.umask(0o077)
if not env.exists():
    with env.open('x') as f:
        f.write('DOCTOR_API_TOKEN='+secrets.token_hex(32)+'\nGROQ_API_KEY=\nGROQ_MODEL=openai/gpt-oss-20b\nGITHUB_TOKEN=\n')
values={}
for line in env.read_text().splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key,value=line.split('=',1)
        values[key.strip()]=value.strip().strip('"').strip("'")
token=values.get('DOCTOR_API_TOKEN','')
if len(token)<32 or not all(c.isalnum() or c in '-_' for c in token):
    raise SystemExit('DOCTOR_API_TOKEN in .env must contain at least 32 letters, digits, hyphens or underscores.')
target=root/'frontend'/'.env.local'
text='DOCTOR_API_TOKEN='+token+'\nDOCTOR_BACKEND_URL=http://127.0.0.1:8080\n'
if target.exists() and target.read_text()!=text:
    raise SystemExit('frontend/.env.local already exists with different values. Align its DOCTOR_API_TOKEN with .env manually; no file was overwritten.')
if not target.exists():
    with target.open('x') as f: f.write(text)
env.chmod(0o600);target.chmod(0o600)
print('Local configuration ready. Keys remain on this machine; repository execution is disabled.')
PY
