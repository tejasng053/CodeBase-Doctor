# Architecture and decisions

## Data flow

The browser submits repository/mode/objective through the Next.js same-origin proxy. Spring creates a UUID job, retrieves public GitHub metadata and pins the default-branch commit. A bounded ZIP snapshot becomes immutable original bytes plus an edit overlay. JavaParser and build metadata readers produce the source map and findings. If the sandbox is available, the baseline is executed offline; otherwise checks remain NOT_RUN.

Groq receives bounded context and allowlisted tools. Planning has no edit capability. A submitted plan records exact paths and a digest. After approval, repair tools read, search, patch and request verification within fixed budgets. Final verification always attempts a clean snapshot of the changed source. Reports derive facts from saved evidence and actual diffs. A separate publication approval controls GitHub writes.

## State and storage

One worker serializes repository work; four active/queued tasks are allowed. Jobs transition QUEUED -> RUNNING -> AWAITING_APPROVAL -> RUNNING -> COMPLETED, or FAILED/CANCELLED. Without Groq, read-only analysis transitions directly to COMPLETED. Completion means the attempt ended, not that builds/tests passed. Job locks protect transitions and patches; snapshots returned to clients are copies. Persistent metadata and source overlays are stored separately with atomic writes. On restart, in-progress jobs fail explicitly and lose approval.

## Extension points

`LanguageAnalyzer` separates source analysis; `LlmProvider` separates reasoning from Groq transport. The sandbox command policy chooses fixed Maven/Gradle commands. New ecosystems must add parsing, command policy, immutable runtime/helper support and tests rather than exposing shell commands. Persistence can later move behind the store boundary to a database. The current JSON store suits local bounded history.

## Important decisions

- Safe source snapshot instead of host clone: no host Git process/hook or checked-out executable scripts. History, submodules and LFS are unsupported. The pipeline's Clone label refers to retrieving a pinned source snapshot.
- Exact byte overlay instead of model shell: approved digest and file scope guard every patch. Git status/diff tools report snapshot changes. They do not claim a local Git branch exists.
- Real remote branch only at publication: branch names are reserved during repair; GitHub Git Data API creates the commit/reference only after a separate approval. Diff exports are standard full-file unified hunks, not minimal Git hunks.
- Offline verification: dependencies are baked into a reviewed image cache. This intentionally limits supported repositories. No untrusted dependency download phase runs on the host.
- Java analysis is syntactic: annotations, packages, class-level relationships and source locations are grounded in JavaParser; no runtime dependency injection graph or database topology is invented. Metadata does not resolve every Maven property/profile or Gradle script.
- CSS components instead of Tailwind/shadcn: the small local UI uses a dedicated stylesheet and typed components to match the owner's portfolio direction without extra dependencies.
- Human-readable job reports are Markdown and printable HTML. Development milestone PDFs are authored separately with full file inventories.

## Main modules

`repository`: bounded HTTPS, archive and path policy. `analysis`: Java/Spring metadata and symbols. `agent`: provider, protocol and patch tools. `service`: workflow and tool dispatch. `sandbox`: Docker lifecycle and verified offline commands. `report`: factual Markdown/HTML. `github`: approved immutable publication. `store`: private local evidence. `api/config`: transport and local authorization. Frontend components render these records; no synthetic progress/test data is shipped.

See FILE_GUIDE.md for every authored file, API.md for routes, SECURITY.md for threat boundaries and VALIDATION.md for executed checks.
