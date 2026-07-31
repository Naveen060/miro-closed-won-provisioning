# Design decisions

## NetSuite availability and five-request limit

The production design uses a durable queue before NetSuite. Salesforce events are acknowledged only after they are safely stored. A dedicated worker takes messages from the queue and allows at most five active NetSuite requests.

During the scheduled Sunday outage, the worker pauses NetSuite delivery. Messages remain in the queue for later processing. After NetSuite returns, the worker drains the backlog without exceeding five concurrent requests.

Every message has a stable idempotency key. Retries use exponential backoff and jitter. Messages that exceed the retry policy move to a dead-letter queue and appear as `NEEDS_ATTENTION` rather than disappearing.

I would monitor queue depth, oldest message age, retry count, dead-letter count, circuit state, and active NetSuite permits.

## Correlation and observability

Salesforce creates a UUID correlation ID for the Closed Won event. Workato saves it in the lifecycle table and passes it in `X-Correlation-Id` to Java, NetSuite, and Zendesk.

Each component logs a small structured event containing:

- correlation ID
- event or opportunity ID
- workflow step
- result
- duration
- retry number
- safe error category

Datadog or Splunk can reconstruct one order by searching for the correlation ID. Logs exclude raw request bodies, names, email addresses, tokens, and authorization headers.

## Credentials and PII

Each external system receives its own least-privilege service identity. Workato Connections store the credentials and recipes reference those connections. Production secrets come from the approved enterprise vault and never enter Git or data tables.

For rotation, I would create the replacement credential, update the Workato connection, test it, then revoke the old credential. This overlap avoids downtime.

PII protection uses allow-listed log fields, masking, access controls, encryption, short retention, and audited access. Debug logging is temporary and never records credentials or complete customer payloads.

## Slack AI and MCP

The Slack agent should read stored lifecycle state instead of calling Salesforce, NetSuite, and Zendesk during every question.

A read-only MCP tool can expose:

```text
get_provisioning_status(opportunityId?, accountName?)
```

It returns the current state, completed steps, safe failure category, next retry time, and last update time. It omits secrets and unnecessary PII.

The MCP gateway maps the Slack user to company permissions, handles ambiguous account names, and audits every query. A separate retry tool would require stronger authorization and explicit confirmation.
