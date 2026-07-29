# Seven-minute walkthrough script

## 0:00-0:40 - Frame the problem

"A Salesforce Closed-Won webhook enters the iPaaS, the Java service validates the deal, NetSuite creates the customer, and Zendesk provisions support. The design goal is at-least-once delivery without duplicate side effects. The important failure boundary retries Zendesk without re-entering NetSuite."

Show the architecture diagram and requirement-coverage table in the root README.

## 0:40-1:30 - Show the recipe

Open the Workato recipe or import `orchestration/n8n/closed-won-provisioning.json` into n8n.

Point out:

- webhook trigger;
- stable correlation ID;
- step-scoped idempotency keys;
- validation HTTP action;
- NetSuite before the retry boundary; and
- retry enabled only on the Zendesk node/monitor block.

## 1:30-2:20 - Happy-path demo

Run:

```powershell
.\scripts\run-demo.ps1 -Scenario success
```

Call out the HTTP 200 validation, one NetSuite customer, one Zendesk organization, and final `PROVISIONED` result.

## 2:20-3:30 - Required Zendesk 500 demo

Run:

```powershell
.\scripts\run-demo.ps1 -Scenario transient-failure
```

Narrate the evidence:

- Zendesk attempt 1 returns the simulated 500;
- only Zendesk is retried;
- attempt 2 succeeds;
- `netSuiteCalls` is exactly 1; and
- `zendeskCalls` is exactly 2.

This directly proves the assignment's saga invariant.

## 3:30-5:00 - Code tour

Open `InMemoryIdempotencyService.java`.

Explain that `ConcurrentHashMap.putIfAbsent` atomically chooses one caller, while all same-key/same-payload callers await one shared `CompletableFuture`. Point out the SHA-256 payload fingerprint, HTTP 409 on key/payload mismatch, success-only caching, and exact-candidate removal after a failure.

Open `InMemoryIdempotencyServiceTest.java`. Highlight the 24-thread simultaneous-release test and the assertion that the business operation runs once.

Open `ApiKeyFilter.java`, `OrderValidationRequest.java`, and `OrderValidationApiTest.java` to show API-key auth, required fields, HTTP 400 behavior, and exact replayed response.

## 5:00-6:30 - Production architecture

Return to the README architecture diagram. Cover:

- durable Pub/Sub ingestion and a saga store;
- a dedicated NetSuite queue and globally capped five-request dispatcher;
- Sunday circuit-open window, retained messages, backoff, and DLQ;
- correlation ID plus trace context across every hop;
- Workato Connections backed by a secret manager and PII allow-list logging; and
- the read-only `get_provisioning_status` MCP tool over a status projection.

Stress that in-memory idempotency is the exercise constraint, not the production recommendation.

## 6:30-7:00 - Close

Run or show the result of:

```powershell
.\mvnw.cmd test
```

Close with: "The prototype proves the API contract, strict race-safe idempotency, and the Zendesk-only retry. The production design adds durable state, queued backpressure, cross-instance idempotency, and governed read access for AI."

