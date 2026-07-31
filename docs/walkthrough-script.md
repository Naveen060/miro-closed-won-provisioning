# Demonstration outline

This outline fits a five-to-ten-minute recording.

## 1. Problem and design

Explain that Salesforce sends a Closed Won event to Workato. Workato validates the order, creates a NetSuite customer, and creates a Zendesk organization.

Point out the main rule: a Zendesk retry must never create the NetSuite customer again.

## 2. Java service

Show:

- `POST /api/v1/orders/validate`
- required `accountId` and `totalAmount`
- API-key filter
- correlation-ID filter
- in-memory idempotency implementation
- concurrent-request test

Run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean verify
```

Expected result: seven tests pass and the build succeeds.

## 3. Workato recipe

Show the recipe from Step 1 through Step 17.

Call out:

- event duplicate check;
- lifecycle data table;
- Java validation call;
- NetSuite before the monitor block;
- only Zendesk inside the monitor block;
- `NEEDS_ATTENTION` failure update;
- `PROVISIONED` success update.

## 4. Happy path

Run `HappyPath` and show:

- successful Workato job;
- one NetSuite call;
- one Zendesk call;
- populated downstream IDs;
- `PROVISIONED` table state.

## 5. Required Saga path

Run `ZendeskTransientRecovery` and show:

- first Zendesk attempt returns 500;
- Workato retries the monitored Zendesk action;
- the second attempt succeeds;
- NetSuite ran only once;
- final state is `PROVISIONED`.

Use the correlation ID to connect the job, HTTP calls, and lifecycle row.

## 6. Production considerations

Briefly cover:

- durable queue and five-request NetSuite worker;
- Sunday outage backlog and recovery;
- Datadog/Splunk correlation search;
- Workato Connections and secret rotation;
- PII-safe logs;
- read-only provisioning status MCP tool for Slack.

End by stating which pieces are working prototypes and which are production architecture proposals.
