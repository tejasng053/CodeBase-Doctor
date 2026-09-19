# Milestone delivery plan

Build one milestone at a time. Debug it, run its applicable checks, record the evidence and limitations in `PROJECT_CONTEXT.md`, and show the user the reviewable result. **Wait for the user's explicit confirmation before starting the next milestone.** Failed checks must be fixed or clearly reported before asking to advance. Approval of a milestone does not authorize future repository edits, builds, commits, or publishing outside the product's separate action approvals.

| Milestone | Status | Deliverable | Checks before review |
| --- | --- | --- | --- |
| 1. Foundation | READY FOR REVIEW | Project layout, local UI/API foundation, configuration guidance, safety boundaries, and this delivery plan. | Build and check the foundation's own code; verify local startup, UI/API connection, configuration errors, and accurately reported unavailable capabilities. |
| 2. Safe snapshot and ingestion | NOT STARTED | Validate public GitHub inputs; bounded repository acquisition and source snapshots; explicit size and path limits; safe cancellation and cleanup. | Exercise invalid URLs, private-network destinations, traversal, symlinks, oversized repositories, timeouts, and cleanup. Confirm no repository code runs on the host. |
| 3. Java intelligence | NOT STARTED | Java/Spring detection, build metadata, modules, structural symbols and relationships, and findings with file/line evidence. | Check Maven and Gradle fixtures, secure XML rejection, annotation-based layers, unresolved references, parser failures, and separation of observations from suspected defects. |
| 4. Sandbox builds | NOT STARTED | Controlled baseline builds/tests with real output, resource/time limits, restricted mounts/network, and fail-closed execution when sandbox prerequisites are absent. | Verify the implemented container boundaries and failure paths; exercise timeout, cancellation, resource limits, and honest test-result parsing. Record the exact isolation tested. |
| 5. Groq plans | NOT STARTED | Server-side Groq integration and bounded read-only diagnosis/planning tools; reviewable plans grounded in inspected files. | Check missing keys, provider failures, budgets, malformed tool requests, repository prompt injection, and plans without fabricated evidence. |
| 6. Approved repair tools | NOT STARTED | Explicit plan approval, dedicated branch, bounded file edits, genuine progress events, and reviewable diffs. | Reject edits without approval and unsafe paths; validate cancellation, changed-plan approval invalidation, and accurate changed-file/diff records. |
| 7. Verification and reports | NOT STARTED | Real post-change builds/tests and downloadable documentation covering repository overview, principal changes, per-file reasons, before/after evidence, and limitations. | Check failing, passing, partial, timed-out, and unrun cases; validate report facts against stored evidence and diff; render hostile Markdown safely; verify the download. See `DOCUMENTATION_FEATURE.md`. |
| 8. GitHub approval and draft PR | NOT STARTED | Separately approved commit/push actions and optional draft pull request with the report's factual summary. | Verify approval is bound to the reviewed diff; reject default-branch pushes; test auth/API failures and accidental duplicate actions. A draft PR remains the default. |
| 9. End-to-end example and release | NOT STARTED | A deliberately broken Spring example, complete documented workflow, tested setup, and release notes with known limitations. | Run the example only in the approved sandbox. Demonstrate baseline failure → reviewed plan → approved repair → real verification → report, including cancellation and failure recovery. |

## Current boundary

Only the foundation is in progress. All subsequent milestones require confirmation before work starts. A preliminary analyzer was authored during initial exploration; it is an unverified scratch draft outside the delivered product, not a completed Java intelligence feature.

## Safety evidence

The design calls for isolated repository execution, narrow filesystem access, and explicit approvals. These are requirements until implemented and checked; this document does not claim rootless Docker or any other isolation has already been proven. Container isolation shares the host kernel and cannot guarantee protection against every hostile repository. Record the tested environment and residual limits when reviewing the sandbox milestone.

At each review gate, create a PDF in `milestone-reports/` containing the folder tree, each created or changed file and purpose, debugging evidence, and known limits. This is required before requesting confirmation.
