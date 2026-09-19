# Repository and change documentation

**Status: planned for Milestone 7; not implemented.**

Every completed or interrupted analysis/repair run should provide a readable report in the application and a downloadable Markdown file. It must explain what the repository does, what the bot mainly changed, why each file changed, and what the recorded checks establish. A scan without edits still produces a repository overview and findings report.

## Report contents

1. **Run context:** repository URL, analyzed revision where available, objective or scan mode, timestamps, final run state, working branch, and report generation time.
2. **Repository overview:** observed languages and declared versions, Maven/Gradle configuration, Spring Boot usage, modules, entry points, tests, and important configuration files. Distinguish declared metadata from successfully executed build information.
3. **Architecture:** principal packages and annotation-supported layers, meaningful repository relationships, and source file/line references. Describe the source snapshot's scope; unresolved dependencies and runtime behavior remain unknown unless independently observed.
4. **Main changes:** a short plain-language description of the principal behavior changed and its connection to the user's objective. State clearly when no edits were made. Label an intended effect as unverified when execution does not demonstrate it.
5. **Per-file explanation:** changed path, what changed, why it changed, additions/deletions from the actual diff, and relevant evidence. Provide the exact diff or a review/download link alongside branch/revision context.
6. **Before and after:** separate baseline and final build/test results, recorded commands, exit status, timing, and test counts only when reported by a supported results source. Link to or include bounded real output and explain truncation. A missing check reads **Not run**; unavailable counts read **Not reported**. An exit code alone must not invent a test count.
7. **Findings and evidence:** separate deterministic observations from suspected problems; include severity, inspected file/line references, explanation, and suggested next action. Do not imply that every finding was repaired.
8. **Remaining concerns:** unresolved failures, incomplete inspection, unsupported configuration, provider/sandbox errors, cancelled work, unverified effects, and any checks omitted or blocked.
9. **Inspection record:** the files actually inspected and the evidence used to produce the report. Where known, identify whether each reference belongs to the baseline or final revision.

## Trust and factuality rules

- Generate run state, changed files, counts, and verification results from recorded events, structured results, and the actual diff. Model-written explanations must agree with that evidence.
- Treat repository text, issue text, filenames, tool output, and model responses as untrusted content. Escape HTML and unsafe Markdown, use safe code fences, and validate any generated links. A report must not execute scripts or turn repository instructions into application actions.
- Keep secrets and provider tokens out of report inputs and output. Redact detected sensitive values in retained logs and snippets; disclose redaction without claiming perfect secret detection.
- Report partial work honestly. Cancellation or a failed build can still produce useful documentation, but must not become a success report.
- Keep the report snapshot consistent with the diff being reviewed. Later edits or reruns must regenerate the report and invalidate stale action approvals as required by the approval design.

## Acceptance checks

Use fixed evidence fixtures for: a scan with no edits; an approved repair with real recorded baseline/final results; an unchanged failure; a command timeout; an interrupted run; missing test counts; an unparsed Java file; and hostile Markdown in repository content. Check that every version, file count, change statistic, result, and architecture claim is traceable to its source.

Verify the UI report and downloaded Markdown describe the same run and remain readable after download. Complete these checks, debug failures, update project context, and obtain the user's confirmation before advancing to the GitHub publishing milestone.
