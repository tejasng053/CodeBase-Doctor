# Deliberately broken Spring expiry fixture

This small Java 21 / Spring Boot application gives Codebase Doctor a reproducible boundary defect. It has an HTTP controller, a constructor-injected service, an injectable UTC clock, and four focused JUnit tests. The exact-deadline assertion intentionally disagrees with the implementation. This fixture is not a production authentication service.

## Safety and execution

Do **not** run this repository's Maven build or tests on the host. Repository code must run only inside Codebase Doctor's approved and verified sandbox. The main project's backend tests do not include or execute this separate example project. Merely shipping these test sources is not evidence that they ran.

To use the public-GitHub workflow, place the fixture in a repository you control and explicitly choose whether to publish that repository. Supply its URL and the objective from `ISSUE.md` to Codebase Doctor. This example does not publish itself, supply a working public URL, bypass sandbox checks, or install Docker.

## Intended demonstration

1. Review the repository overview: Maven, Java 21, Spring Boot, controller, service, and tests.
2. Collect baseline build/test output through the sandbox. Expected from source inspection: the exact-deadline assertion fails. Mark the baseline unverified until a real run records that result.
3. Review the repair plan and approve only the intended implementation change.
4. Inspect the resulting diff and run the final build/tests through the same sandbox.
5. Download the report and verify it describes the principal comparison change, its reason, inspected files, and the real before/after results.

The desired repair is deliberately small: expiry must include equality. A correct report must never claim the expected four-test outcome was observed unless execution actually produced it.
