# Security boundaries

## Host and local API

Single-user local operation is the supported mode. Both services use loopback addresses. Every backend endpoint requires the server token. The Next.js proxy validates Host and browser origin, rejects cross-site requests, limits JSON bodies and proxies an explicit route allowlist. Provider tokens stay server-side. Public hosting requires a separate authentication, authorization, quota and operational-security design.

Untrusted archives are parsed as bounded byte maps, not extracted to host source folders. URLs are restricted to GitHub HTTPS and the code uses fixed GitHub/codeload endpoints without redirects. ZIP paths, duplicates, symlinks/special entries, file counts and expanded sizes are checked. Local persistence contains untrusted data; no host repository shell or Git hook runs. Application dependencies/builds are trusted development work, distinct from inspected repository code.

## Repository execution

Docker shares the host kernel; these controls cannot guarantee complete PC safety. Use a dedicated VM for unfamiliar repositories. The controller requires rootless Docker, seccomp, cgroup v2/systemd, a trusted labeled image and no declared image volumes. Every created container rechecks actual limits before importing code.

Containers use an immutable image ID, read-only root, no network, dropped capabilities, no-new-privileges, non-root helper/build identities, 1 CPU, 1,536 MiB memory with no extra swap, 128 process limit, bounded tmpfs and a fixed lifetime. There are no host bind mounts, credentials, SSH agents or Docker sockets inside repository containers. The helper runs under a distinct UID; build code cannot replace it. Build processes are quiesced before collecting reports. Reports are bounded regular files with XML entity/DTD rejection and symlink/hardlink checks. Tool timeouts and output caps apply. Cancellation removes containers and retries uncertain cleanup.

Static analysis can run without execution prerequisites. The runner never falls back to host commands or relaxes controls. Image labels identify the expected image contract, not cryptographic provenance; operators must build/review their own trusted image. Current base image tags should be replaced with reviewed digests for a distributed release.

## Model and publication

Issue/repository text is untrusted model context. Tools and runtime state enforce permissions independently of prompts. The model cannot call a host shell, arbitrary URL, GitHub publish or force-push. Patches require the exact approved file scope, current content digest, unambiguous replacement and size/path checks. Sensitive paths and workflow/hook configuration edits are blocked. Plan approval and publication approval have different digests.

Publishing is constrained to a new `codebase-doctor/<UUID>` reference and source commit. It never updates default/protected branches and refuses stale source heads or changed reviewed data. The GitHub token belongs to the controller only. Existing remote CI may execute after branch/PR creation; approval authorizes those ordinary GitHub consequences. Test success does not itself authorize publication.

## Data and residual risks

Groq receives selected repository context when configured. Known credentials and common secret patterns are redacted from model observations, events and reports, and common sensitive filenames are blocked from model reads. This does not prove all arbitrary embedded secrets are removed. Public repositories may still contain secrets; inspect them before submission. Snapshots deliberately preserve original source bytes locally for honest diffs and therefore may contain such source material. No claim of complete secret scanning is made.

JSON job persistence is private-directory, bounded and atomically replaced, with 20-job retention. Restart invalidates active approvals. This is not a multi-user database or tamper-proof audit log. Source parsers, archive libraries and Docker still need patching and runtime security review. Resource limits reduce denial-of-service risk, not all attacks. Windows/macOS engines and shared/rootful Docker are not supported execution environments for this MVP.

## Validation status

Archive/path/patch/approval/report/cgroup-policy behavior is covered by automated tests. Live static source acquisition and browser/API boundaries were checked. Docker daemon access was denied in this session. No actual container isolation, image build, hostile workload containment, live Groq repair or GitHub publication was verified. See `VALIDATION.md` for evidence.
