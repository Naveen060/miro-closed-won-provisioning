# Submission review

## Verdict

The repository is implementation-complete for the code, runnable orchestration proof, architecture write-up, and walkthrough preparation. Three account/user-bound submission actions remain: publish the repository publicly, create or import the live iPaaS recipe in the candidate's chosen tenant, and record the narrated video.

## Rubric audit

| Part | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | HTTP webhook | n8n Webhook node and Workato trigger build sheet | Pass |
| 1 | Validation hop | n8n/Workato HTTP step calls the Java endpoint | Pass |
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
| 4 | Demo flow | Seven-minute script and one-command executable demo | Ready to record |
| Delivery | Public GitHub repository | Local repository exists but has not been published | Manual |
| Delivery | Live iPaaS tenant artifact | Importable n8n JSON and Workato build sheet exist | Manual/account-bound |

## Verification performed

- Maven build on Java 21: success.
- JUnit suite: 7 tests, 0 failures, 0 errors.
- Synchronized concurrency proof: 24 callers, 1 execution, 23 replays.
- Happy-path process test: provisioned; NetSuite 1, Zendesk 1.
- Required failure process test: provisioned; NetSuite 1, Zendesk 2 after simulated HTTP 500.
- Node.js syntax checks: pass.
- n8n JSON and connection-reference checks: pass.
- PowerShell parser check: pass.
- Repository-owned process cleanup: pass.
- Likely hard-coded production secret scan: no findings; the documented `local-demo-key` is intentionally a mock credential.

## Reviewer-facing strengths

- The saga behavior is proven with counts, not only described.
- Idempotency handles simultaneous arrivals, payload/key conflicts, replay equality, and retry after failure.
- The production design cleanly distinguishes the exercise's in-memory constraint from a durable multi-instance system.
- Correlation, security, PII, and MCP access controls are treated as end-to-end concerns.

## Remaining presentation choices

- Workato is Miro's preferred tool. If a Workato developer sandbox is available, build the short recipe from `docs/workato-recipe.md` and show that in the video. Otherwise, state up front that n8n is the chosen similar iPaaS and import the checked-in workflow JSON.
- Keep the video near seven minutes. Spend the most time on the Zendesk-only retry boundary and the atomic idempotency implementation; those are the strongest differentiators.
