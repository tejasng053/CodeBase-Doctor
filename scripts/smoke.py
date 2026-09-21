#!/usr/bin/env python3
"""Local API boundary smoke checks. Requires dev.sh to be running."""
import json
import urllib.request
import urllib.error

BASE = 'http://127.0.0.1:3000'


def check(path, expected, method='GET', headers=None, body=None):
    request = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        content = response.read()
        assert response.status == expected, (path, expected, response.status, content[:150])
    print(method, path, expected, 'PASS')
    return content


health = json.loads(check('/api/health', 200))
assert health['status'] == 'UP' and health['ingestionEnabled'] is True
assert 'DOCTOR_API_TOKEN' not in json.dumps(health)
assert isinstance(json.loads(check('/api/jobs', 200)), list)
check('/api/jobs', 400, 'POST', {'Content-Type': 'application/json', 'Origin': BASE}, b'{}')
check('/api/jobs', 403, 'POST', {'Content-Type': 'application/json', 'Origin': 'https://untrusted.example'}, b'{}')
check('/api/health', 403, headers={'Host': 'untrusted.example'})
check('/api/health', 403, headers={'Sec-Fetch-Site': 'cross-site'})
check('/api/unrecognized', 404)
check('/api/jobs', 413, 'POST', {'Content-Type': 'application/json', 'Origin': BASE}, b'x' * 24001)
print('All 8 local API boundary smoke checks passed.')
