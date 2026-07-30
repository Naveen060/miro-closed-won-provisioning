# Assignment summary and implementation plan

## What the brief asks for

Build a prototype for a Salesforce Closed-Won event that:

1. starts from an HTTP webhook;
2. calls a Java or Kotlin service to validate the order;
3. creates a mock NetSuite customer;
4. provisions a mock Zendesk organization; and
5. catches a simulated Zendesk HTTP 500 and retries only Zendesk, without creating a duplicate NetSuite customer.

The validation service must expose `POST /api/v1/orders/validate`, use a mock API key, return HTTP 400 when `accountId` or `totalAmount` is missing, cache successful responses by `Idempotency-Key`, and be safe when identical calls arrive simultaneously.

The README must also address NetSuite's hypothetical five-request concurrency cap and two-hour Sunday outage, end-to-end correlation and observability, secret rotation and PII-safe logging, and a read-only Tool/MCP design for provisioning-status questions from a Slack LLM agent.

Finally, the candidate records a 5-10 minute walkthrough showing a successful run, the Zendesk failure/retry, and the concurrency/idempotency code.

## Solution plan

| Layer | Choice | Reason |
|---|---|---|
| Validation service | Java 21 + Spring Boot | Clear HTTP, validation, security, and test primitives |
| Idempotency | Concurrent map + shared future + payload hash | Makes the race winner explicit and proves one execution |
| iPaaS artifact | Workato recipe | Uses Miro's stated primary platform and the required HTTP/retry actions |
| Recipe specification | Workato build sheet + webhook test harness | Makes the tenant-owned recipe reproducible and testable |
| External systems | Dependency-free Node.js mocks | Deterministic success, replay, and one-time Zendesk 500 |
| Verification | JUnit integration/concurrency tests + end-to-end runner | Covers behavior and provides demo-ready evidence |

## Delivery sequence

1. Implement API contract, auth, validation, correlation, and structured errors.
2. Implement strict idempotency and a same-millisecond concurrency test.
3. Implement idempotent downstream stubs and Zendesk-only retry orchestration.
4. Build the Workato recipe and its repeatable webhook test harness.
5. Run unit, integration, concurrency, and Workato end-to-end scenarios.
6. Review the repository line by line against every assignment bullet.
