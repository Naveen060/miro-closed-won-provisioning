# Production readiness

This prototype keeps orchestration in Workato and business validation in a small Java service. The Workato lifecycle table is the durable operational record for every Closed Won event.

## NetSuite downtime and concurrency

In production, I would place accepted Closed Won events on a durable queue before calling NetSuite. Workato consumers would process that queue with a maximum concurrency of five. A scheduled control would pause NetSuite consumption during the assumed two-hour Sunday outage while events continue accumulating safely.

Every request carries an idempotency key derived from the event or opportunity. Temporary failures use exponential backoff with jitter. Exhausted retries move the record to `NEEDS_ATTENTION` and generate an alert; a scheduled reconciliation recipe later finds stale records and safely resumes them. This design prevents event loss and prevents duplicate NetSuite customers.

## Observability

Salesforce supplies an event ID and correlation ID. Workato stores both in the lifecycle table and passes the correlation ID through `X-Correlation-Id` to the validation service, NetSuite mock, Zendesk mock, and alert endpoint. Each service returns the same value and includes it in structured logs.

Datadog or Splunk can therefore search one value and reconstruct the complete lifecycle: webhook receipt, validation, NetSuite creation, Zendesk retries, final provisioning, or failure. Logs contain identifiers, state, duration, HTTP status, and error category—not customer PII.

## Credentials, rotation, and PII

Secrets belong in managed Workato Connections or an enterprise vault integration, never in recipes, payload examples, source code, or logs. Each system uses a separate least-privilege service identity. Rotation creates a new credential, updates and verifies the connection, and then revokes the old credential without changing recipe logic.

Sensitive datapills are masked. Operational logs include event, correlation, opportunity, state, and error metadata only. Email addresses, names, addresses, tokens, and full request bodies are excluded or redacted. Access to connections and job details is restricted and audited.

## Slack and MCP status tool

I would expose a read-only `get_provisioning_status` tool backed by a callable Workato recipe. It accepts an opportunity ID or account name, searches the lifecycle table, and returns the current state, completed systems, retry information, last safe error summary, and last-updated time.

An MCP server or Slack LLM Agent calls this tool instead of reading Workato directly. The tool uses least-privilege authentication, returns no unnecessary PII, records an audit event, and never allows the model to update provisioning state.
