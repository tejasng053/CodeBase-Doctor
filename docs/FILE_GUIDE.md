# Complete file guide

This lists every authored source, configuration, test, asset and guide. Private .env values, dependencies, runtime caches/data and generated PDFs are excluded. The milestone field names the principal delivery stage; shared changes are described in the stage reports.

## Folder tree

```text
.dockerignore
.env.example
.gitignore
PROJECT_CONTEXT.md
README.md
backend/
  pom.xml
  src/
    main/
      java/
        dev/
          codebasedoctor/
            DoctorApplication.java
            agent/
              GroqLlmProvider.java
              LlmProvider.java
              SourceTools.java
            analysis/
              JavaSpringAnalyzer.java
              LanguageAnalyzer.java
            api/
              ApiErrors.java
              FoundationController.java
            config/
              LocalApiGuard.java
              Redactor.java
            github/
              GitHubPublisher.java
            model/
              Models.java
            report/
              ReportGenerator.java
            repository/
              BoundedHttp.java
              RepositoryPolicy.java
              RepositorySource.java
            sandbox/
              DockerSandbox.java
              SandboxPolicy.java
            service/
              DoctorService.java
            store/
              JobStore.java
      resources/
        application.properties
    test/
      java/
        dev/
          codebasedoctor/
            FoundationApiTest.java
            agent/
              SourceToolsTest.java
            analysis/
              JavaSpringAnalyzerTest.java
            github/
              GitHubPublisherTest.java
            report/
              ReportGeneratorTest.java
            repository/
              RepositoryPolicyTest.java
            sandbox/
              DockerSandboxTest.java
              SandboxPolicyTest.java
            service/
              DoctorServiceTest.java
docker/
  backend.Dockerfile
  frontend.Dockerfile
  guard.py
  sandbox.Dockerfile
  test_guard.py
docker-compose.yml
docs/
  API.md
  ARCHITECTURE.md
  DOCUMENTATION_FEATURE.md
  FILE_GUIDE.md
  MILESTONES.md
  MILESTONE_1_VALIDATION.md
  RELEASE_NOTES.md
  SECURITY.md
  USER_GUIDE.md
  VALIDATION.md
  file-inventory.json
  milestone-data.json
  report-format.md
  validation-results.json
examples/
  broken-spring-app/
    ISSUE.md
    README.md
    pom.xml
    src/
      main/
        java/
          example/
            tokens/
              TokenApplication.java
              TokenController.java
              TokenExpiryService.java
      test/
        java/
          example/
            tokens/
              TokenExpiryServiceTest.java
frontend/
  AGENTS.md
  CLAUDE.md
  app/
    api/
      [...path]/
        route.ts
    globals.css
    layout.tsx
    page.tsx
  components/
    icons.tsx
    job-panels.tsx
    workbench.tsx
  lib/
    client.ts
    types.ts
  next-env.d.ts
  next.config.ts
  package-lock.json
  package.json
  public/
    favicon.svg
    fonts/
      DMMono-Regular.ttf
      Manrope-Variable.ttf
      SpaceGrotesk-Variable.ttf
      dmmono-OFL.txt
      manrope-OFL.txt
      spacegrotesk-OFL.txt
  tsconfig.json
milestone-reports/
  README.md
scripts/
  dev.py
  dev.sh
  generate_reports.py
  preflight.sh
  setup-local.sh
  smoke.py
  test.sh
  test_dev.py
```

## Each file and its purpose

### .dockerignore

Retained foundation file | Milestone 1

Excludes secrets, dependencies, build output, and local runtime data from Docker build contexts.

### .env.example

Updated from foundation | Milestone 9

Secret-free server configuration template for local API, Groq, optional GitHub publishing, sandbox image and data location.

### .gitignore

Retained foundation file | Milestone 1

Keeps credentials, installed dependencies, build artifacts, caches, and runtime data out of Git.

### PROJECT_CONTEXT.md

Updated from foundation | Milestone 9

Canonical future-agent handoff: owner instructions, actual code state, architecture, safety, checks and remaining acceptance tasks.

### README.md

Updated from foundation | Milestone 9

Project overview and startup, capabilities, demo, validation boundaries, documentation navigation and current limits.

### backend/pom.xml

Updated from foundation | Milestone 3

Pins Java/Spring build dependencies, JavaParser, Commons Compress and application testing/package configuration.

### backend/src/main/java/dev/codebasedoctor/DoctorApplication.java

Retained foundation file | Milestone 1

Starts the Spring Boot API and discovers application components.

### backend/src/main/java/dev/codebasedoctor/agent/GroqLlmProvider.java

Created | Milestone 5

Sends bounded tool-calling requests to Groq, validates provider responses and stops safely on missing keys or malformed output.

### backend/src/main/java/dev/codebasedoctor/agent/LlmProvider.java

Created | Milestone 5

Provider abstraction separating the agent loop from Groq HTTP transport.

### backend/src/main/java/dev/codebasedoctor/agent/SourceTools.java

Created | Milestone 6

Reads snapshot files, applies approved digest-checked exact patches and creates true changed-file records and full-file unified diffs.

### backend/src/main/java/dev/codebasedoctor/analysis/JavaSpringAnalyzer.java

Updated from foundation | Milestone 3

Uses JavaParser plus secure XML/literal build metadata parsing to detect stack, modules, Spring layers, symbols, relationships and findings.

### backend/src/main/java/dev/codebasedoctor/analysis/LanguageAnalyzer.java

Retained foundation file | Milestone 3

Defines the source-analysis extension boundary for future ecosystems.

### backend/src/main/java/dev/codebasedoctor/api/ApiErrors.java

Created | Milestone 2

Maps invalid input, missing jobs and invalid workflow states to safe JSON API errors.

### backend/src/main/java/dev/codebasedoctor/api/FoundationController.java

Updated from foundation | Milestone 2

Exposes health, job lifecycle, approvals, cancellation, SSE snapshots, downloads and separately approved publication through REST.

### backend/src/main/java/dev/codebasedoctor/config/LocalApiGuard.java

Retained foundation file | Milestone 1

Requires a strong configured API token, authenticates API calls, and rejects oversized requests.

### backend/src/main/java/dev/codebasedoctor/config/Redactor.java

Created | Milestone 5

Redacts configured tokens and common credential patterns from model observations, logs and reports.

### backend/src/main/java/dev/codebasedoctor/github/GitHubPublisher.java

Created | Milestone 8

Validates publication approval/source integrity, creates new Git objects and doctor references, and optionally creates an approved draft PR.

### backend/src/main/java/dev/codebasedoctor/model/Models.java

Updated from foundation | Milestone 2

Defines typed persisted job, event, analysis, plan, change and verification records shared across modules.

### backend/src/main/java/dev/codebasedoctor/report/ReportGenerator.java

Created | Milestone 7

Generates escaped Markdown and self-contained printable HTML from actual stored analysis, changes and verification evidence.

### backend/src/main/java/dev/codebasedoctor/repository/BoundedHttp.java

Created | Milestone 2

Fixed-deadline HTTPS transport with redirect rejection, bounded bodies and interrupt handling.

### backend/src/main/java/dev/codebasedoctor/repository/RepositoryPolicy.java

Created | Milestone 2

Validates GitHub URLs, source paths, text/size limits and sensitive or prohibited edit paths.

### backend/src/main/java/dev/codebasedoctor/repository/RepositorySource.java

Created | Milestone 2

Pins public GitHub source commits, retrieves same-repo issues and validates ZIP snapshots without host extraction.

### backend/src/main/java/dev/codebasedoctor/sandbox/DockerSandbox.java

Updated from foundation | Milestone 4

Checks engine/image readiness, launches isolated offline containers, streams bounded output, verifies reports and cleans up after failures/cancellation.

### backend/src/main/java/dev/codebasedoctor/sandbox/SandboxPolicy.java

Created | Milestone 4

Validates sandbox jobs, immutable image IDs, source import payloads and fixed execution policy.

### backend/src/main/java/dev/codebasedoctor/service/DoctorService.java

Created | Milestone 5

Coordinates intake, parsing, baseline evidence, bounded model tools, plan approval, repair, cancellation and final reports.

### backend/src/main/java/dev/codebasedoctor/store/JobStore.java

Created | Milestone 2

Atomically persists private bounded job/snapshot data, creates response copies and invalidates interrupted approvals at restart.

### backend/src/main/resources/application.properties

Retained foundation file | Milestone 1

Binds the API to loopback, sets request/error-handling limits, and maps environment-based configuration.

### backend/src/test/java/dev/codebasedoctor/FoundationApiTest.java

Updated from foundation | Milestone 2

Five isolated-store API tests for local authentication, honest readiness, invalid intake, weak startup tokens and oversized requests.

### backend/src/test/java/dev/codebasedoctor/agent/SourceToolsTest.java

Created | Milestone 6

Checks patch approval/digests, creations/deletions, actual diffs, stale publication review and provider response shape.

### backend/src/test/java/dev/codebasedoctor/analysis/JavaSpringAnalyzerTest.java

Created | Milestone 3

Thirteen tests for Java/Spring parsing, Maven/Gradle detection, reference grounding, unsafe XML and metadata corner cases.

### backend/src/test/java/dev/codebasedoctor/github/GitHubPublisherTest.java

Created | Milestone 8

Four mocked HTTP tests verify new doctor branches/draft payloads, retry behavior, stale or tampered state and branch collision rejection.

### backend/src/test/java/dev/codebasedoctor/report/ReportGeneratorTest.java

Created | Milestone 7

Sixteen regression tests for factual reports, unknown counts, malicious markup, secret redaction and quoted diff paths.

### backend/src/test/java/dev/codebasedoctor/repository/RepositoryPolicyTest.java

Created | Milestone 2

Five tests reject unsafe URLs, traversal, symlink ZIP entries, oversized sources and prohibited paths.

### backend/src/test/java/dev/codebasedoctor/sandbox/DockerSandboxTest.java

Created | Milestone 4

Nine fake-transport tests exercise readiness, isolation flags, results, streaming and container lifecycle behavior without executing Docker.

### backend/src/test/java/dev/codebasedoctor/sandbox/SandboxPolicyTest.java

Created | Milestone 4

Three tests enforce bounded imports, source-path safety and execution policy.

### backend/src/test/java/dev/codebasedoctor/service/DoctorServiceTest.java

Created | Milestone 5

Five mocked workflow tests cover exact approval, allowed edits, missing sandbox, late cancellation and report-error cleanup.

### docker-compose.yml

Updated from foundation | Milestone 9

Local static-analysis UI/API deployment with a private named data volume, loopback exposure and no Docker socket mount.

### docker/backend.Dockerfile

Updated from foundation | Milestone 9

Builds the Spring service image and prepares writable private persistence for the unprivileged runtime user.

### docker/frontend.Dockerfile

Retained foundation file | Milestone 1

Builds the frontend with all source directories and runs its standalone production server as a non-root user.

### docker/guard.py

Created | Milestone 4

Immutable container helper: checks enforced limits, imports bounded files, invokes fixed offline builds, quiesces build processes and parses real JUnit reports.

### docker/sandbox.Dockerfile

Created | Milestone 4

Builds the trusted Java/Maven/Gradle execution image with independent non-root helper/build users and a reviewed offline dependency seed.

### docker/test_guard.py

Created | Milestone 4

Seventeen Python tests for cgroup checks, archive import bounds, safe JUnit parsing, offline commands and process/report safety.

### docs/API.md

Created | Milestone 9

Documents authenticated local REST/SSE endpoints, payload meanings, error states and approval boundaries.

### docs/ARCHITECTURE.md

Created | Milestone 9

Explains data flow, extension boundaries, persistence, source-snapshot decisions and implementation tradeoffs.

### docs/DOCUMENTATION_FEATURE.md

Updated from foundation | Milestone 7

Defines the implemented end-user report covering repository overview, principal/per-file changes and verification evidence.

### docs/FILE_GUIDE.md

Created | Milestone 9

Readable complete folder tree and per-file purpose inventory for handoff and maintenance.

### docs/MILESTONES.md

Updated from foundation | Milestone 9

Tracks nine consolidated delivery stages and their mapping to the original twelve-stage brief.

### docs/MILESTONE_1_VALIDATION.md

Retained foundation file | Milestone 1

Records exact Milestone 1 checks, debugging fixes, observed results, and unverified limitations.

### docs/RELEASE_NOTES.md

Created | Milestone 9

Records delivered behavior, debugging and exact runtime acceptance limits for the local MVP.

### docs/SECURITY.md

Created | Milestone 9

Describes local API, archive, model, sandbox and publishing boundaries, residual risks and validation limits.

### docs/USER_GUIDE.md

Created | Milestone 9

Gives setup, Groq/Docker configuration, review/publish workflow, reports, data management and troubleshooting steps.

### docs/VALIDATION.md

Created | Milestone 9

Records executed checks and distinguishes mocked integration evidence from unrun external acceptance checks.

### docs/file-inventory.json

Created | Milestone 9

Structured authored-file inventory with purpose, principal milestone and creation/modification classification.

### docs/milestone-data.json

Created | Milestone 9

Machine-readable stage titles, implementation status, checks and limitations used for PDF generation.

### docs/report-format.md

Created | Milestone 7

Explains the report renderer contract, evidence hierarchy, escaping and exact diff-derived counts.

### docs/validation-results.json

Created | Milestone 9

Machine-readable final test suite counts, live-scan evidence and explicit runtime blockers.

### examples/broken-spring-app/ISSUE.md

Created | Milestone 9

Human-readable objective for repairing the bundled token-expiry example.

### examples/broken-spring-app/README.md

Created | Milestone 9

Documents the intentionally broken fixture, expected bug and safe sandbox-only demonstration procedure.

### examples/broken-spring-app/pom.xml

Created | Milestone 9

Defines the Java 21/Spring Boot Maven fixture and its JUnit/Spring test dependencies.

### examples/broken-spring-app/src/main/java/example/tokens/TokenApplication.java

Created | Milestone 9

Boot entry point for the intentionally broken Spring demonstration repository.

### examples/broken-spring-app/src/main/java/example/tokens/TokenController.java

Created | Milestone 9

Example REST endpoint exposing token-expiry behavior for a realistic repair target.

### examples/broken-spring-app/src/main/java/example/tokens/TokenExpiryService.java

Created | Milestone 9

Intentionally incorrect expiry boundary used to demonstrate diagnosis and scoped repair.

### examples/broken-spring-app/src/test/java/example/tokens/TokenExpiryServiceTest.java

Created | Milestone 9

Regression cases specifying the expected expiry behavior; execute only inside the approved sandbox.

### frontend/AGENTS.md

Retained foundation file | Milestone 1

Next.js-generated development guidance for future coding agents; created automatically by the dev server.

### frontend/CLAUDE.md

Retained foundation file | Milestone 1

Next.js-generated reference to the local agent guidance file.

### frontend/app/api/[...path]/route.ts

Updated from foundation | Milestone 9

Guards same-origin browser requests and proxies allowed API/SSE/download routes with server-only credentials and bounded bodies.

### frontend/app/globals.css

Updated from foundation | Milestone 9

Portfolio-inspired theme palette, locally served fonts, floating navigation, editorial hero, result panels and responsive/reduced-motion rules.

### frontend/app/layout.tsx

Retained foundation file | Milestone 1

Defines the page document, application metadata, and global stylesheet import.

### frontend/app/page.tsx

Retained foundation file | Milestone 1

Renders the main Codebase Doctor workbench page.

### frontend/components/icons.tsx

Created | Milestone 9

Reusable local SVG icons for navigation, workflow, downloads and status controls.

### frontend/components/job-panels.tsx

Created | Milestone 9

Renders overview, architecture, findings, plan, activity, terminal, per-file diff and report downloads from real job records.

### frontend/components/workbench.tsx

Updated from foundation | Milestone 9

Interactive UI shell, source form, history hydration, real SSE/polling, plan approval, publication review, theme and navigation.

### frontend/lib/client.ts

Created | Milestone 9

Typed API client, bounded request timeouts, job display helpers and safe GitHub link validation.

### frontend/lib/types.ts

Updated from foundation | Milestone 9

TypeScript contracts for actual API health, job, analysis, findings, plans, verification and publishing state.

### frontend/next-env.d.ts

Updated from foundation | Milestone 1

Provides generated Next.js type declarations for TypeScript.

### frontend/next.config.ts

Updated from foundation | Milestone 9

Configures standalone builds, local compiler checks and browser security headers with production eval disabled.

### frontend/package-lock.json

Retained foundation file | Milestone 1

Locks resolved frontend dependency versions for repeatable installation.

### frontend/package.json

Retained foundation file | Milestone 1

Defines Next.js/React/TypeScript dependencies and development, build, start, and type-check commands.

### frontend/public/favicon.svg

Retained foundation file | Milestone 1

Provides the application-specific browser icon.

### frontend/public/fonts/DMMono-Regular.ttf

Created | Milestone 9

Locally served DMMono typeface matching the portfolio; avoids browser requests to external font services.

### frontend/public/fonts/Manrope-Variable.ttf

Created | Milestone 9

Locally served Manrope typeface matching the portfolio; avoids browser requests to external font services.

### frontend/public/fonts/SpaceGrotesk-Variable.ttf

Created | Milestone 9

Locally served SpaceGrotesk typeface matching the portfolio; avoids browser requests to external font services.

### frontend/public/fonts/dmmono-OFL.txt

Created | Milestone 9

OFL license and attribution for the bundled dmmono font.

### frontend/public/fonts/manrope-OFL.txt

Created | Milestone 9

OFL license and attribution for the bundled manrope font.

### frontend/public/fonts/spacegrotesk-OFL.txt

Created | Milestone 9

OFL license and attribution for the bundled spacegrotesk font.

### frontend/tsconfig.json

Retained foundation file | Milestone 1

Defines TypeScript compiler checks and frontend import aliases.

### milestone-reports/README.md

Updated from foundation | Milestone 9

Indexes the historical/new milestone PDFs, complete handbook, source data and regeneration instructions.

### scripts/dev.py

Updated from foundation | Milestone 9

Safely parses configuration as data and supervises backend/frontend process groups, including compiled standalone UI preview.

### scripts/dev.sh

Updated from foundation | Milestone 9

Runs idempotent local setup and forwards development/production-preview options to the supervisor.

### scripts/generate_reports.py

Created | Milestone 9

Reproducibly renders eight later milestone PDFs and the complete handbook from saved facts, guides and the file inventory.

### scripts/preflight.sh

Created | Milestone 4

Read-only Docker prerequisite checks; never installs services or changes socket permissions.

### scripts/setup-local.sh

Updated from foundation | Milestone 9

Creates/reuses local random token configuration, rejects inconsistent files and restricts private settings to mode 0600.

### scripts/smoke.py

Updated from foundation | Milestone 9

Eight live local HTTP checks for readiness, history, invalid intake and browser/proxy authorization boundaries.

### scripts/test.sh

Updated from foundation | Milestone 9

Runs configuration and guard regression checks, Maven tests and the frontend production build.

### scripts/test_dev.py

Retained foundation file | Milestone 1

Checks that configuration stays literal and cannot override host command lookup, and that service process groups are terminated.
