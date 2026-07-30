# Submission review

## Verdict

The repository is implementation-complete for the code, Workato recipe specification, batch-test harness, architecture write-up, and walkthrough preparation. Three account/user-bound submission actions remain: publish the repository publicly, finish and test the live Workato recipe, and record the narrated video.

## Rubric audit

| Part | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | HTTP webhook | Workato webhook trigger and repeatable test harness | Pass |
| 1 | Validation hop | Workato HTTP step calls the Java endpoint | Pass |
| 1 | NetSuite/Zendesk stubs | Dependency-free mock server with deterministic IDs | Pass |
| 1 | Zendesk 500 without duplicate NetSuite order | Live demo proves NetSuite calls = 1 and Zendesk calls = 2 | Pass |
| 2 | `POST /api/v1/orders/validate` | Spring MVC controller | Pass |
| 2 | Mock API key | Constant-time API-key filter and HTTP 401 test | Pass |
| 2 | Required `accountId`, `totalAmount` | Bean validation and HTTP 400 test | Pass |
| 2 | Strict idempotency | Success cache, SHA-256 request fingerprint, HTTP 409 conflict | Pass |
| 2 | Concurrency safety | 24-way synchronized race test executes operation once | Pass |
| 3 | NetSuite outage and limit | Durable queue, five-permit dispatcher, circuit breaker, DLQ | Pass |
| 3 | Observability | Correlation/trace propagation, saga links, dashboards and alerts | Pass |
| 3 | Credentials and PII | Workato Connections/vault, rotation, allow-list logging | Pass |
| 3 | AI-first mandate | Read-only status projection and MCP tool schema | Pass |
| 4 | Demo flow | Seven-minute script and Workato batch-test runner | Ready to record |
| Delivery | Public GitHub repository | Local repository exists but has not been published | Manual |
| Delivery | Live iPaaS tenant artifact | Workato build sheet exists; live tenant recipe is account-bound | Manual/account-bound |

## Verification performed

- Maven build on Java 21: success.
- JUnit suite: 7 tests, 0 failures, 0 errors.
- Synchronized concurrency proof: 24 callers, 1 execution, 23 replays.
- Workato batch runner: nine deterministic cases with correlation-ID reports.
- Required Workato evidence: happy path plus Zendesk retry with no duplicate NetSuite customer.
- Mock-service Node.js syntax checks: pass.
- PowerShell parser check: pass.
- Repository-owned process cleanup: pass.
- Likely hard-coded production secret scan: no findings; the documented `local-demo-key` is intentionally a mock credential.

## Reviewer-facing strengths

- The saga behavior is proven with counts, not only described.
- Idempotency handles simultaneous arrivals, payload/key conflicts, replay equality, and retry after failure.
- The production design cleanly distinguishes the exercise's in-memory constraint from a durable multi-instance system.
- Correlation, security, PII, and MCP access controls are treated as end-to-end concerns.

## Remaining presentation choices

- Build the short recipe from `docs/workato-recipe.md`, run the batch harness, and show the matching Workato Jobs in the video.
- Keep the video near seven minutes. Spend the most time on the Zendesk-only retry boundary and the atomic idempotency implementation; those are the strongest differentiators.
