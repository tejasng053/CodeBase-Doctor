# Validation record - 20 September 2026

## Executed checks

- Backend: **64 tests passed**, no failures/errors. Maven command from backend: `mvn -q -Dmaven.repo.local=../.runtime/m2 test`.
- Python: **17 sandbox helper tests + 2 configuration tests passed**. These do not execute external repository scripts.
- Frontend: `npm run build` passed production compilation, TypeScript checks, static page generation and trace collection.
- Local HTTP: **8 API boundary smoke checks passed**: actual readiness/history, invalid intake, cross-origin/Host/Fetch-Site rejection, unknown route and oversized body.
- `docker compose config --quiet` passed configuration parsing only.
- Browser: verified portfolio-inspired dark/light desktop styling, phone layout at 390px without horizontal overflow, saved-history reopening, complete documentation and no console errors observed. Space Grotesk is the rendered heading font; fonts are served locally.

## Real source and documentation path

A browser-started scan of `spring-guides/gs-rest-service` completed using source commit `3f4cef01152596c19bc4d37939409358812b1421`. It found 6 Java declarations and recorded Java 17/Maven, multiple Spring versions and nested module evidence. The overview, architecture and downloadable Markdown/HTML were inspected. Baseline build and tests correctly remain NOT_RUN because the Docker daemon is inaccessible. There were no source edits or GitHub writes.

## Tests are not live provider evidence

Service workflow tests use explicit mock source/Groq/runner boundaries. They check plan approval, patch scope, before/after recording, late cancellation and cleanup on report failure. GitHub tests use a mocked HTTP transport, checking new doctor references, draft PR payloads, source/approval integrity, moved base heads, collisions and duplicate prevention. Sandbox Java tests use a fake Docker transport; Python tests use controlled cgroup/report fixtures. These prove application policy behavior, not real isolation or successful repository execution.

## Debugging completed

Corrected JavaParser/DOM Node ambiguity, report escaping of generated HTML entities, quoted Git diff paths, saved-run detail loading, cancellation/approval task tracking, publication snapshot consistency and report refresh. API tests now use a temporary store so existing local history cannot affect results. Added bounded line streaming and interruption-safe sandbox cleanup. Reports preserve unknown test counts and distinguish recorded workflow completion from verification success.

## Unverified boundaries

`preflight.sh` reports Docker daemon permission denied. No socket permission was changed and no untrusted code ran on the host. The trusted sandbox image has not been built/run here; resource enforcement, cache adequacy, real Groq repair and actual GitHub branch/PR publication remain acceptance tasks. Compose runtime startup is also unverified. Full end-to-end MVP acceptance cannot be claimed until those checks run in the intended environment.

Machine-readable suite results are in `validation-results.json`. Generated milestone PDFs include the relevant checks and limitations rather than substituting mock tests for live execution.
