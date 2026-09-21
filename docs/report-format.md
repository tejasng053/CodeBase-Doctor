# Repository report format and evidence rules

`ReportGenerator.generate(Job)` creates Markdown from the recorded job snapshot. `generateHtml(Job)` wraps the same escaped Markdown text in a self-contained, script-free HTML document suitable for downloading or printing. The HTML intentionally preserves report text rather than interpreting arbitrary repository Markdown.

## Sections

- **Run context:** repository, requested objective, state, reserved review branch, pinned source revision, recorded timestamps, and publication metadata when available.
- **Main changes:** text line statistics derived from the actual unified diff and a separately labeled agent explanation. A model-provided explanation is not test evidence.
- **Repository overview:** declared ecosystem versions and modules, snapshot file count, parsed Java types, annotation-supported layers, and structural repository relationships with file/line references. This is a source snapshot, not a runtime call graph; references can precede later edits.
- **File-by-file changes:** actual patch statistics, what changed, and why. A missing diff prevents statistics from being treated as verified. Quoted Git paths, including escaped quotes and octal UTF-8 sequences, are decoded for statistics. Unsupported or malformed headers are disclosed instead of inventing a path. Counts describe the recorded patch, which may use complete-file replacement hunks, rather than the smallest possible edit. Binary changes do not have meaningful text line counts.
- **Before and after verification:** separate baseline/final build/test statuses, counts, duration, and recorded output. Missing runs read `Not run`; missing counts read `Not reported`. Timeouts, failures, and skipped work remain visible. No test count is inferred from an exit code.
- **Static findings:** deterministic source observations separated from suspected issues, each with available evidence and suggested action. Absence of findings is not proof of correctness.
- **Remaining concerns:** errors, cancellation, incomplete checks, explicit concerns, snapshot limits, and redaction limitations.
- **Inspection and patch:** actual file-inspection records, branch/revision context, and the recorded diff. A same-server `/api/jobs/{id}/diff` link appears only for a valid run identifier.

## Content safety and size limits

Untrusted text is HTML-escaped and Markdown metacharacters are escaped in one pass so generated entities remain valid. Multiline inline fields cannot inject headings or table rows. Tool output and patches use fences longer than any embedded backtick run. The HTML document escapes the entire report and permits no scripts or remote resources.

Recognizable API-token patterns, named credential assignments, and Authorization values are redacted. This is best-effort redaction, not a guarantee that all secrets are detected; review reports before sharing. Per-command report excerpts stop at 32,768 characters and disclose truncation. Architecture and finding tables show at most 250 entries each and disclose any additional omitted entries. Full available patch text remains in the report subject to the upstream job's own limits.

## Static analyzer scope

`JavaSpringAnalyzer` reads source text as data. It does not execute build scripts or configuration. XML parsing rejects DOCTYPE and external entities. Maven properties, compiler-plugin settings, Spring Boot declarations, literal Gradle versions/toolchains, and literal module includes are recognized. Dynamic Gradle logic and unresolved property inheritance remain unknown.

JavaParser extracts declared classes, records, interfaces, enums, annotations, and nested types. Dependencies are included only when repository types resolve from explicit imports, same-package names, or unambiguous wildcard imports. Generic type parameters do not become repository dependencies. Parsing is structural, not complete Java compiler symbol resolution.

The analyzer accepts at most 2,000 entries, 262,144 characters per file, and 8,388,608 total source characters; omitted entries are disclosed. Unsafe relative paths, null content, and malformed Java cannot silently become valid analysis. Findings currently cover empty catch blocks, Spring field injection, large declared types, and suspected hardcoded credential literals. These rules do not diagnose every bug; the expiry example still requires issue context and real tests.

## Validation

Backend unit tests cover Maven/Gradle detection, safe XML rejection, Java type/annotation relationships, ambiguous and generic names, snapshot limits, malformed source, truthful before/after reporting, diff-derived statistics including the quoted paths emitted by SourceTools, untrusted Markdown/HTML, safe fences, redaction, cancellation, and missing evidence. They parse strings and generate reports; they do not execute the deliberately broken Spring fixture.
