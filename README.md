# Codebase Doctor

Diagnose. Repair. Verify.

A local developer tool for understanding and eventually repairing Java/Spring repositories with explicit human approval. The application will use Groq for reasoning; Codex is used to develop it.

**Current state: Milestone 1 — foundation. Repository analysis and execution are deliberately disabled.** The interface, Spring API, authenticated local connection, and read-only Docker diagnostics are implemented. Later workflow panels are empty and do not represent completed operations.

Development proceeds one milestone at a time: implement, debug, show evidence, obtain your confirmation, then continue. See [milestones](docs/MILESTONES.md).

## Run locally

Requirements: Java 21, Maven 3.9+, Node 20.9+ (Node 22 recommended), npm, Python 3. Docker and Groq credentials are **not required for this foundation**.

From the project folder:

```bash
./scripts/setup-local.sh
cd frontend
npm ci --ignore-scripts
cd ..
./scripts/dev.sh
```

Open **http://127.0.0.1:3000**. Use this exact address; the frontend validates its configured origin. The API binds to `127.0.0.1:8080`. Stop the development command with Ctrl+C.

`setup-local.sh` creates a unique local API token in ignored, permission-restricted `.env` and `frontend/.env.local`. The browser never receives this token. No API key is necessary yet. Never paste credentials into a repository issue or commit `.env`.

Maven dependencies stay under `.runtime/m2`. The first backend run downloads official build dependencies. The application itself does not download or execute repositories at this milestone.

## Validate

```bash
./scripts/test.sh
```

This runs the configuration parser regression test, backend tests, and frontend production build. With the app running, `python3 scripts/smoke.py` verifies the real local connection and rejected unsafe requests. It does not run code from external repositories. Check [validation notes](docs/MILESTONE_1_VALIDATION.md) for what was actually executed and any remaining limitations.

## Architecture

```text
Browser at 127.0.0.1:3000
  → Next.js App Router / same-origin API proxy
  → Spring Boot 3.5 / Java 21 on 127.0.0.1:8080
  → read-only Docker diagnostics

Future approved milestones:
  → safe repository intake → JavaParser analysis
  → hardened Docker runner → Groq tool loop
  → review and approval → bounded repair → real verification
  → documentation and diff → separately approved draft PR
```

The local API requires a token even for read access. The frontend only proxies named endpoints to a loopback backend, validates the browser origin and host, and limits request bodies. Repository execution always returns disabled, including if Docker is available. Groq and GitHub keys are configuration placeholders only; this milestone sends neither API requests nor repository data to those services.

## PC safety

No repository is cloned, analyzed, built, edited, committed, or pushed at this stage. The only child commands are fixed, read-only Docker version/info probes. There is no model shell or file editing endpoint. The app never requests elevated privileges or changes Docker socket permissions.

Before execution is implemented, the design must enforce separate job workspaces, no sensitive host mounts or Docker socket in repository containers, non-root execution, dropped capabilities, resource bounds, cancellation, controlled network access, and an approval gate before edits. Docker shares the host kernel and cannot guarantee protection from every hostile workload; a dedicated VM will remain the recommended execution boundary for unfamiliar repositories. See [Docker's security model](https://docs.docker.com/engine/security/) and [rootless mode](https://docs.docker.com/engine/security/rootless/).

## Documentation feature

An approved later milestone will generate a downloadable Markdown report with:

- A repository overview, detected stack, modules, and grounded architecture map.
- A short explanation of the bot's main change and why it matters.
- Per-file changes and reasons, with links to actual source locations and diff.
- Actual baseline and final build/test results; unknown or unrun checks remain explicit.
- Verified findings, suspected concerns, remaining limitations, and inspected files.

See [the report contract](docs/DOCUMENTATION_FEATURE.md). This feature is specified, not yet implemented.

## Docker Compose

A foundation-only Compose configuration is included:

```bash
./scripts/setup-local.sh
docker compose up --build
```

It publishes only `127.0.0.1:3000`, shares a private network namespace between UI and API, and mounts no host filesystem or Docker socket. It is **not a repository sandbox**. Container startup cannot be verified on this PC session because the Docker daemon is inaccessible; local Java/Node startup is the primary validated path.

## Current limits and contributing

Single-user, local foundation only. No job persistence, ingestion, Java analysis, agent loop, repair, generated reports, GitHub publication, authentication accounts, billing, teams, or public hosting. Read `PROJECT_CONTEXT.md` before working on this project. Every milestone needs its own debugging evidence and explicit owner confirmation before the next begins. No open-source license is selected yet; choose a license before public distribution.
# CodeBase-Doctor
