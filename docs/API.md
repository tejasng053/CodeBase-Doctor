# Local API

Browser requests use `/api` at http://127.0.0.1:3000. The frontend attaches `X-Doctor-Token` server-side to the backend at http://127.0.0.1:8080. Direct API clients must provide that token. Never expose it in URLs. POST requests through the frontend require JSON and the exact local Origin. Bodies are capped at 24,000 bytes at the proxy.

| Method | Route | Meaning |
| --- | --- | --- |
| GET | /api/health | Actual analysis, Docker, Groq and publication readiness |
| GET | /api/jobs | Recent job summaries; fetch detail before showing full results |
| POST | /api/jobs | Create job with repository, objective and mode (SCAN/SOLVE) |
| GET | /api/jobs/{id} | Full immutable response copy of stored job state |
| GET | /api/jobs/{id}/events | SSE snapshot events and keepalives; reconnect supported |
| POST | /api/jobs/{id}/approve | Body approvalDigest; authorize the current plan |
| POST | /api/jobs/{id}/cancel | Interrupt queued/running/awaiting work |
| GET | /api/jobs/{id}/report | Markdown attachment |
| GET | /api/jobs/{id}/report.html | Escaped HTML attachment with restrictive CSP |
| GET | /api/jobs/{id}/diff | Actual unified patch attachment |
| POST | /api/jobs/{id}/publish | Body publicationDigest and createDraftPr; separate GitHub authorization |

Job records include actual events, analysis, plan, baseline/final verification, inspected files, changes, diff, report, concerns, source commit and publication results. Test counts can be null. Status values include NOT_RUN, UNSUPPORTED, SKIPPED, NO_TESTS, UNVERIFIED, PASSED, FAILED and TIMEOUT. Consumers must not convert unknown counts to fabricated totals.

Errors use JSON with a safe message. Invalid input is 400; unknown jobs 404; invalid state/approval is 409; proxy authorization failures 403 and oversized input 413. Provider/network errors are saved on jobs when asynchronous. No endpoint accepts shell commands, arbitrary filesystem paths or arbitrary network destinations.
