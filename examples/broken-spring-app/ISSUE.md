# Token remains valid at its exact expiry instant

The expiry contract says that a token is expired when the current time is **at or after** its deadline. `TokenExpiryService.isExpired` currently treats a token as valid when the injected clock is exactly equal to `expiresAt`.

Reproduction fixture: `TokenExpiryServiceTest.tokenIsExpiredAtItsExactDeadline` uses a fixed UTC clock and the same instant for the deadline. The expected result is `true`. The present implementation returns `false`.

Please correct the boundary comparison while retaining the existing behavior immediately before and after expiry and for a missing deadline. Keep the injected clock so the tests remain deterministic. Run the real tests inside the approved sandbox and report the baseline/final evidence. Do not weaken, remove, or skip the failing test.

Acceptance: at the exact instant the token is expired; a deadline one nanosecond in the future is still valid; a deadline one nanosecond in the past is expired. The documentation should explain the changed comparison and distinguish expected behavior from actually observed test results.
