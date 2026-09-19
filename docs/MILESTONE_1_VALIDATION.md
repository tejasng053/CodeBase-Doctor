# Milestone 1 validation - 19 September 2026

Status: **ready for owner review**. This validates the foundation only. Repository ingestion, repair, generated product reports, and publishing are not implemented or enabled.

## Checks actually run

| Check | Result | Evidence |
| --- | --- | --- |
| Spring Boot foundation tests | PASS | 5 tests, 0 failures, 0 errors, 0 skipped; `backend/target/surefire-reports/dev.codebasedoctor.FoundationApiTest.txt` |
| Configuration / process regression tests | PASS | 2 tests: literal allowlisted .env parsing, service process-group termination |
| Frontend production build | PASS | Next.js 16.3.5 compiled, type-checked and generated `/`, `/_not-found`, `/api/[...path]`; standalone output present |
| Explicit TypeScript check | PASS | `npm run typecheck` exited 0 |
| Real local HTTP smoke checks | PASS | 8 checks through the running UI proxy: health 200, empty jobs 200, submission 409, foreign Origin 403, foreign Host 403, cross-site fetch 403, unknown route 404, oversized body 413 |
| Browser checks | PASS | UI reported API Connected and Docker unavailable; Documentation and Activity navigation and issue-mode switch worked; Analyze stayed disabled; no captured browser console errors |
| Responsive inspection | PASS | Mobile preview plus observed 900px and 1440px layout widths; measured no horizontal overflow at the latter two sizes |
| Browser bundle secret scan | PASS | Actual local API token absent from all production `.next/static` files |
| Local configuration permissions | PASS | `.env` and `frontend/.env.local` are mode 0600 |
| Shell syntax | PASS | Startup, setup and test scripts parsed successfully |
| Compose configuration | PASS | `docker compose config --quiet` exited 0 |
| Docker container startup | NOT RUN | Docker CLI exists; this session cannot access its daemon. No permission changes or privileged bypass attempted. |
| Groq calls / real repository workflow | NOT RUN | Outside this milestone; no provider key supplied, all execution disabled |

## Debugging fixes

- Replaced shell sourcing of `.env` with allowlisted literal parsing; configuration cannot run shell command substitutions.
- Replaced parent-PID-only shutdown with process-group termination and a regression test.
- Aligned frontend Docker packaging with standalone output and included the component source directory.
- Fixed the workbench's default import during the TypeScript check.
- Used Next.js's TypeScript compiler API because this execution environment returned empty captured CLI output. Type checking remains enabled and passed independently.
- Confirmed services bind to loopback and the proxy supplies the token only on the server.

## Review boundary

The UI is a foundation workspace, not a working repair agent. The documentation tab describes a later feature, and its download control remains disabled. The milestone PDF is provided separately in `milestone-reports/Milestone-01-Foundation.pdf`.

Milestone 2 requires owner confirmation. Its scope is safe, bounded public GitHub intake and workspace preparation. No external repository code has been run on this PC.
