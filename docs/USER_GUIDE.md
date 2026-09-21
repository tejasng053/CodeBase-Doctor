# User guide

## Start and scan

Run the setup and development commands in the root README. Open http://127.0.0.1:3000. Environment shows actual readiness. Enter a repository root URL such as https://github.com/spring-guides/gs-rest-service. Doctor Scan permits an empty objective. Solve an issue requires issue text, an objective, or a same-repository GitHub issue URL.

The repository must be public. Intake pins the default branch commit and retrieves its archive. Invalid hosts, credential-bearing URLs, traversal and symlinks are rejected. No scripts run during ingestion or static analysis. GitHub rate limits or unavailable archives produce a failed run with a report; retry by starting a new run.

Overview lists detected stack, important files and modules. Architecture lists parsed symbols and source-level relationships. Findings are deterministic observations or suspected concerns with evidence; neither a clean scan nor a completed job guarantees defect-free code. Plan contains the model's proposed changes. Activity and Terminal show actual recorded operations/output. Diff shows full-file unified hunks and per-file selection. Documentation explains the repository and changes and provides downloads.

## Enable Groq

Add `GROQ_API_KEY` and `GROQ_MODEL` to the ignored root `.env`, then restart. Default model: `openai/gpt-oss-20b`; select an available model supporting local tool calls if account access changes. The provider uses https://api.groq.com/openai/v1/chat/completions. See [Groq tool documentation](https://console.groq.com/docs/tool-use/overview).

The application sends bounded repository metadata, selected readable source, issue text, findings and tool observations to Groq. Filename rules and known-secret redaction reduce accidental leakage but are not a comprehensive secret scanner. Planning can consume provider quota. Missing keys leave static analysis available; invalid keys/rate limits stop reasoning with a visible error. There is no automatic provider switch or billing operation.

## Enable repository execution

Use a dedicated VM for unfamiliar repository code. Configure a rootless Docker engine yourself using [Docker's official guide](https://docs.docker.com/engine/security/rootless/). This app never uses sudo, modifies socket permissions or installs the engine. Rootless, seccomp, cgroup v2 and systemd cgroup enforcement are required; an unrestricted rootful daemon is rejected.

Review `docker/sandbox.Dockerfile` and `docker/guard.py`, then build the trusted image from the project root:

```bash
docker build -f docker/sandbox.Dockerfile -t codebase-doctor-sandbox:local .
./scripts/preflight.sh
```

Image construction needs network for official tool distributions and reviewed Maven cache dependencies. It does not execute the broken example's source: cache preparation uses a trusted generated passing test. Repository execution later has **no network**. The image contains Java 21, Maven, Gradle and the immutable helper. Its seed cache targets the bundled Spring Maven example; arbitrary dependencies are not preloaded. Gradle projects usually need a reviewed offline cache extension. Change trusted cache inputs and rebuild; never enable networking for untrusted builds as a shortcut.

A plan is needed before source edits. Approval is bound to the current source revision, objective and plan. Only listed files may change. Changing scope requires a new run. Builds/tests use fixed offline commands in new transient containers. Build scripts remain executable code inside the sandbox; controls reduce exposure but cannot guarantee protection against all kernel/runtime defects. See security documentation.

## Review, cancel and retry

Use Cancel for queued, running or awaiting-approval jobs. The app interrupts work and removes job containers; uncertain cleanup is retried. Review any recorded partial edits in the final report. Interrupted jobs do not resume automatically after a server restart, and old approvals are cleared. Start a fresh analysis to retry.

A COMPLETED status means the workflow attempt ended. Read separate build/test statuses; failed, missing, skipped, unsupported and unrun checks remain explicit. A zero test exit code without usable reports is not treated as passing tests. Model summaries are explanations, not evidence of correctness.

## Publish after review

Set a fine-grained GitHub token restricted to your intended repository, with Contents read/write and Pull requests read/write. Review Diff, Terminal and Documentation, then use Review publication. Confirm the exact repository, doctor branch, changed files and verification. A separate checkbox authorizes GitHub writes; draft PR is on by default. Publication may still be possible with failed verification, so the UI calls attention to those results.

The server checks the reviewed digest, persisted workspace and current default-branch head. It creates blobs, a tree, a commit and a new `codebase-doctor/<job-id>` reference. It never updates main/master/develop, force-pushes or merges. If the default branch moved, start a new analysis. GitHub may trigger existing repository automation on any new branch/PR; review the target repository's workflow behavior before approval. Network failure may leave a branch without a PR; the report records known remote results and retries check for an existing branch/PR. Do not delete remote objects blindly after uncertain failures.

## Reports and saved data

Each run stores metadata, evidence and a pinned source snapshot under `backend/data` by default. Downloads are Markdown, escaped self-contained HTML and a `.patch` file. Open the HTML locally and use the browser's Print / Save as PDF for a per-run PDF. The milestone PDFs shipped with this project explain development and files, rather than a user's analyzed repository.

History is capped at 20 jobs. Stop the app and move the private data directory to a backup location before starting a fresh history; this preserves recoverability. Keep source snapshots private. `.env`, caches, data and dependencies are ignored by Git. To relocate data, set an absolute `DOCTOR_DATA_DIR` in `.env`. Never share `.env` or `frontend/.env.local`.

## Troubleshooting

- Backend unavailable: use the exact configured origin, start both services, confirm Java/Maven/Node requirements and that the server tokens match.
- Docker unavailable: static scans still work. Read `preflight.sh` output; do not bypass the readiness gate.
- Offline dependency failure: prepare a reviewed image cache; the app will not download dependencies during repository execution.
- Root build missing: nested samples can be analyzed, but the current runner only executes the repository root.
- Plan/API failure: inspect Activity, provider configuration and budget limits; start a new run.
- Unsupported Java version: the runtime is Java 21. A newer target is not silently downgraded.
