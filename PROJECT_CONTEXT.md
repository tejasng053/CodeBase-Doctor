# Codebase Doctor - project context

Read this file before any future work. Update it after each meaningful milestone. Never claim a feature works without verification.

## Vision
A local Java/Spring repository doctor: understand a codebase, diagnose issues, propose a plan, obtain approval, edit safely, run real tests, and provide a reviewable diff and plain-English documentation. Groq is the intended application LLM; Codex is the development assistant.

## Owner instructions and working directory
- Project: `/home/tejas-ng/Desktop/coding/projects/code base Doctor`.
- Work one milestone at a time, debug it, then obtain explicit confirmation before proceeding.
- At every completed milestone, create a PDF in `milestone-reports/` with folder structure, every created/changed project file and its purpose, changes, checks, and limits.
- Keep the PC safe. Never run untrusted repository build scripts on the host, change Docker socket permissions, or silently use a host fallback.
- Build the end-user documentation feature: repository overview, principal changes, per-file reasons, and real before/after evidence.

## Current milestone
Milestone 1 - foundation, validated and ready for owner review. Milestone 2 has not started. Full milestone gates: `docs/MILESTONES.md`. Verification evidence: `docs/MILESTONE_1_VALIDATION.md`.

## Architecture and stack
Next.js 16.3.5 / React 19.3 / TypeScript App Router UI -> same-origin guarded proxy -> Spring Boot 3.5.16 / Java 21 API. UI `127.0.0.1:3000`; backend `127.0.0.1:8080`. DockerSandbox currently provides read-only engine diagnostics only.

## Directory structure
- `frontend/`: local UI, API proxy, typed future workflow contract.
- `backend/`: Spring API, local token guard, read-only Docker diagnostics, foundation tests.
- `docker/`, `docker-compose.yml`: foundation containers; no runner or socket mount.
- `scripts/`: safe local setup, service supervisor, tests and smoke checks.
- `docs/`: milestone gates, product documentation contract, validation evidence.
- `milestone-reports/`: one PDF per completed milestone.
- `examples/`: reserved, no broken application is delivered yet.

## Implemented
Foundation API health and empty job list. Repository submission returns 409 and cannot start a job. Server-side token validation. Fixed read-only Docker probes with timeouts and bounded output. Setup generates a random local token and chmod 0600 configuration. Supervisor parses allowlisted .env values without executing shell expressions and terminates service process groups. Frontend production build and TypeScript checks passed. Live UI/API checks and browser interactions passed; see the validation report.

## Partially implemented / not implemented
Only shared model types and UX contracts exist for future jobs. No ingestion, persistent jobs, JavaParser analysis, executable sandbox, Groq loop, repair plan, edits, tests of external repositories, diff generation, generated user report, commit/push, or draft PR. No accounts, billing, teams, public deployment, or supported production multi-user mode. Preliminary later-feature drafts written before milestone steering are kept outside this project as scratch; they are not delivered or verified functionality.

## Security decisions
Loopback binding and backend token protect local access. UI origin/Host checks prevent cross-origin mutations and DNS rebinding through the proxy. Provider keys never reach browser code. Repository execution remains disabled regardless of engine status. Future sandbox requires rootless execution, resource controls, no sensitive mounts/socket/keys, safe path/patch validation and approval gates. A dedicated VM remains recommended for hostile code; Docker is not an absolute guarantee.

## Configuration
`DOCTOR_API_TOKEN` (generated, local server secret), `GROQ_API_KEY`, `GROQ_MODEL`, `GITHUB_TOKEN` (unused placeholders this milestone). Frontend server also uses `DOCTOR_BACKEND_URL` and optional `DOCTOR_FRONTEND_ORIGIN`. Never expose token values or commit `.env`/`.env.local`.

## Run and test
`./scripts/setup-local.sh`, then `cd frontend && npm ci --ignore-scripts`, then from root `./scripts/dev.sh`. `./scripts/test.sh` runs configuration tests, backend tests, frontend build. `python3 scripts/smoke.py` checks a running foundation. Runtime dependencies/builds are ignored in `.runtime`, `node_modules`, `.next`, and `target`.

## Known limitations
The current session cannot access the Docker daemon. Its rootless/cgroup properties are not verified. Compose configuration can be validated but container startup remains untested. No Groq/GitHub keys were supplied or used. Application repair and report workflows are explicitly disabled until their approved milestones.

## Next task
Present Milestone 1 and its completed PDF; request owner confirmation for Milestone 2 safe repository intake. Do not start it automatically.
