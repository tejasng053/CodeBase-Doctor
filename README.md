# Codebase Doctor

**Diagnose. Repair. Verify.** A local Java/Spring workbench with repository analysis, a Groq tool loop, reviewable repairs, and a report explaining what changed.

The UI follows the owner's portfolio direction: restrained dark/light colors, large typography, numbered sections, rounded controls, and subtle motion. It shows actual job events, source evidence, output and diffs.

## What works

- Public GitHub repository and issue intake, pinned to a source commit, with bounded archive validation.
- JavaParser-based Java/Spring structure, Maven/Gradle metadata, layers, dependencies and evidence-backed findings.
- Saved jobs, progress over SSE, cancellation, overview, architecture, findings, plan, terminal, diff and documentation panels.
- Groq planning and bounded repair tools with approval bound to the exact plan and allowed file paths.
- A Docker runner that refuses execution unless rootless Docker, enforced resource controls and the trusted helper image are available.
- Markdown and printable HTML reports: repository overview, principal change, per-file explanation, baseline/final checks and remaining concerns.
- Separately approved GitHub Git Data API publication to a new doctor branch, with a draft PR selected by default.

**Validation boundary:** live public-repository scanning and report downloads are verified. Automated tests exercise repair and publishing with test doubles. Actual Docker execution, a live Groq repair and GitHub publication are not verified on this PC: Docker access and provider credentials were unavailable. See [validation](docs/VALIDATION.md). This is a single-user local MVP, not a verified public multi-user service.

## Run locally

Requirements: Java 21, Maven 3.9+, Node 20.9+ (Node 22 recommended), npm, Python 3. Docker and model keys are optional for static analysis.

```bash
./scripts/setup-local.sh
cd frontend
npm ci --ignore-scripts
cd ..
./scripts/dev.sh
```

For a compiled UI preview, build the frontend first, then run `./scripts/dev.sh --production` from the project root.

Open **http://127.0.0.1:3000**. The exact origin is checked; the API listens on `127.0.0.1:8080`. Ctrl+C stops both services. Maven dependencies are cached in `.runtime/m2`.

Setup creates ignored `.env` and `frontend/.env.local` with a random server token and mode 0600. Edit `.env` as data; it is never executed as shell code. To enable planning, set `GROQ_API_KEY` and `GROQ_MODEL` (default `openai/gpt-oss-20b`). Restart the supervisor after changing settings. Repository context, issue text and tool observations are sent to Groq when configured; inspect your public repository for embedded secrets first. No OpenAI, Claude or Codex API key is required.

For repairs, follow [sandbox setup](docs/USER_GUIDE.md#enable-repository-execution). For optional publishing, set a fine-grained `GITHUB_TOKEN` restricted to the intended repository with Contents read/write and Pull requests read/write. Do not paste keys into the UI. A separate publication review shows the destination, branch, diff and verification before creating GitHub objects.

## Demo workflow

1. Paste a public Java repository URL. Choose Doctor Scan or enter an issue/objective.
2. Review stack, architecture and findings. Without Groq, the run ends with a read-only report.
3. With Groq, review the proposed plan and its exact file scope. Approve only after sandbox readiness is confirmed.
4. Follow real edit/build/test events, then inspect the diff and baseline/final results.
5. Download Markdown or HTML documentation. HTML can be opened locally and printed to PDF.
6. Optionally review and approve publication to the new doctor branch and draft PR.

The intentionally broken token-expiry example is in [examples/broken-spring-app](examples/broken-spring-app/README.md). Do not run that fixture on the host as part of agent verification.

## Architecture

```text
Browser -> guarded Next.js proxy -> Spring Boot API
                                      |-> pinned GitHub source snapshot
                                      |-> Java/Spring analyzer
                                      |-> Groq + allowlisted tools
                                      |-> isolated offline Docker verification
                                      |-> saved evidence + Markdown/HTML report
                                      |-> approved GitHub branch/draft PR
```

Source is stored as bounded data snapshots rather than a host Git checkout. Edits are exact patches to the snapshot; a branch name is reserved at approval and the real remote branch is created only during separately approved publication. Exported unified diffs use full-file replacement hunks. This design avoids host Git hooks and repository command execution. History, submodules and Git LFS hydration are outside this MVP.

## Checks and documentation

```bash
./scripts/test.sh
# While the app is running:
python3 scripts/smoke.py
# Read-only sandbox diagnostics:
./scripts/preflight.sh
```

Read [user guide](docs/USER_GUIDE.md), [security](docs/SECURITY.md), [architecture](docs/ARCHITECTURE.md), [API](docs/API.md), [file guide](docs/FILE_GUIDE.md), [report format](docs/report-format.md) and [milestone status](docs/MILESTONES.md). PDFs are saved in `milestone-reports/`; [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) is the handoff for future work.

## Docker Compose

`docker compose up --build` runs the UI/API for static analysis with a named data volume and no host mounts or Docker socket. Compose does **not** enable repository execution. Native development with a reviewed rootless Docker context is the execution path. Compose configuration validates; image builds/startup were not run in this session.

## Current limits and next work

Public, small repositories only; 3,000 files, 24 MiB expanded, 1 MiB per file and 20 local jobs. Parsing inspects a bounded Java/config subset. Root build files are required for execution; nested standalone projects need a future module selector. Java 21 is the sandbox runtime. Dependencies must already exist in its reviewed offline cache; arbitrary Maven projects and most Gradle projects may fail offline. These are reported failures, never silent host/network fallbacks.

Next priorities are runtime isolation tests on a dedicated rootless VM, a live Groq fixture repair, broader reviewed dependency caches, and dependency-pinned release images. No accounts, teams, billing, non-Java repair, public deployment or automatic merging are included. Read security and context before contributing. No open-source license has been selected; choose one before public distribution.
