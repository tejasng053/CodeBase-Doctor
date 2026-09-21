# End-user documentation contract

Implemented in `ReportGenerator`, exposed as Markdown and printable HTML downloads, and visible in the Documentation panel.

Every report explains the repository's detected language/build/framework, modules, important files and source-level architecture; recorded findings and evidence; the main change; each modified file and its reason; inspected paths; actual source revision and reserved/published branch; actual diff; baseline/final build and test evidence; errors, unrun checks and remaining concerns.

Model-authored explanations are distinguished from deterministic evidence. Diff-derived paths and line counts override ungrounded change claims. No test counts or health score are invented. A failed/no-change scan can still produce useful documentation. Untrusted Markdown and HTML are escaped; code fences adapt to contents. Reports redact known server credentials and common secret patterns but are not a complete secret scanner. See report-format.md for implementation details and tests.

For a PDF of a run, download HTML, open it locally and choose Print / Save as PDF. Development milestone PDFs are separate deliverables saved in milestone-reports with folder trees and every created/changed file purpose.
