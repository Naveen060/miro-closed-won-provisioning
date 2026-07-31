# Design decisions

This document explains the production decisions I would make when moving my prototype into a real Miro environment.

## NetSuite availability and five-request limit

I would place a durable queue before NetSuite. I would acknowledge a Salesforce event only after the queue stores it safely. I would use a dedicated worker that allows at most five active NetSuite requests.

During the scheduled Sunday outage, I would pause NetSuite delivery and keep the messages in the queue. After NetSuite returns, I would drain the backlog without exceeding five concurrent requests.

I would give every message a stable idempotency key. I would use exponential backoff and jitter for retries. Messages that exceed the retry policy would move to a dead-letter queue and appear as `NEEDS_ATTENTION` rather than disappearing.

I would monitor queue depth, oldest message age, retry count, dead-letter count, circuit state, and active NetSuite permits.

## Correlation and observability

I would generate a UUID correlation ID in Salesforce for the Closed Won event. I would save it in the Workato lifecycle table and pass it in `X-Correlation-Id` to Java, NetSuite, and Zendesk.

I would make each component log a small structured event containing:

- correlation ID
- event or opportunity ID
- workflow step
- result
- duration
- retry number
- safe error category

I could then reconstruct one order in Datadog or Splunk by searching for the correlation ID. I would exclude raw request bodies, names, email addresses, tokens, and authorization headers from logs.

## Credentials and PII

I would give each external system its own least-privilege service identity. I would store credentials in Workato Connections and reference those connections from my recipes. Production secrets would come from the approved enterprise vault and would never enter Git or data tables.

For rotation, I would create the replacement credential, update the Workato connection, test it, then revoke the old credential. This overlap avoids downtime.

I would protect PII with allow-listed log fields, masking, access controls, encryption, short retention, and audited access. I would keep debug logging temporary and would never record credentials or complete customer payloads.

## Slack AI and MCP

I would make the Slack agent read the stored lifecycle state instead of calling Salesforce, NetSuite, and Zendesk during every question.

I would expose this read-only MCP tool:

```text
get_provisioning_status(opportunityId?, accountName?)
```

I would return the current state, completed steps, safe failure category, next retry time, and last update time. I would omit secrets and unnecessary PII.

I would use the MCP gateway to map the Slack user to company permissions, handle ambiguous account names, and audit every query. I would keep retry operations in a separate tool that requires stronger authorization and explicit confirmation.
