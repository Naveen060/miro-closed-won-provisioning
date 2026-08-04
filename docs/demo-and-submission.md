# Demo and submission guide

## Suggested demo

1. Show the repository structure and explain the separation between validation, mock downstream systems, Workato assets, and production-readiness notes.
2. Run the Java and mock-system tests locally.
3. Send a unique happy-path webhook and show the lifecycle ending in `PROVISIONED`.
4. Send a transient Zendesk failure. Show NetSuite executing once, Zendesk retrying inside the monitor, and the final state becoming `PROVISIONED`.
5. Replay the same event ID. Show the duplicate branch stopping successfully before validation or downstream calls.
6. Send an invalid order and show the Java service rejecting it.
7. Explain the correlation ID, credential controls, NetSuite queue/concurrency design, reconciliation recipe, and read-only Slack/MCP status tool.

## Submission checklist

- Repository URL and clear README
- Java 21 source and automated tests
- Workato recipe screenshots or exported package when permitted
- Lifecycle table schema and webhook example
- Happy-path, retry, duplicate, and validation evidence
- Production-readiness answers
- Short walkthrough video with no credentials or customer PII visible
