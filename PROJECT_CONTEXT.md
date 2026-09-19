# Codebase Doctor — project context

## Vision and current milestone
Build the Java/Spring repository diagnosis and approved repair workflow described in the supplied brief. Prioritize PC safety, real evidence, and downloadable documentation explaining the repository and principal changes. Foundation in progress; no runtime capability is claimed verified yet.

## Architecture
Next.js TypeScript UI → same-origin API proxy → local Spring Boot Java 21 API → bounded Groq tool loop → hardened Docker sandbox. JavaParser analyzes source as data. Local JSON job snapshots provide modest single-user persistence. Repo builds never execute on the host. Execution fails closed if the required sandbox is unavailable.

## Directory structure
frontend/, backend/, docker/, examples/, docs/, scripts/.

## Security decisions
Local-only application. No host shell tool for the model. No sensitive directory or Docker socket in repository containers. Plan approval precedes mutations. No automated publishing without a separate explicit approval. Untrusted repository data cannot grant permissions. Docker shares the host kernel; a dedicated VM is recommended for hostile repositories.

## Next tasks
Implement sandbox, analyzer, reports, UI and orchestration; test component boundaries; document verified results and remaining limitations. Read this file before making future changes and update it after milestones.
