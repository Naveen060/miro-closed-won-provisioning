# Demonstration outline

I use this outline for my five-to-ten-minute recording.

## 1. Problem and design

I explain that Salesforce sends a Closed Won event to Workato. I then show how Workato validates the order, creates a NetSuite customer, and creates a Zendesk organization.

I point out my main rule: a Zendesk retry must never create the NetSuite customer again.

## 2. Java service

I show:

- `POST /api/v1/orders/validate`
- required `accountId` and `totalAmount`
- API-key filter
- correlation-ID filter
- in-memory idempotency implementation
- concurrent-request test

I run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\services\order-validation\mvnw.cmd `
  -f .\services\order-validation\pom.xml `
  clean verify
```

I expect seven tests to pass and the build to succeed.

## 3. Workato recipe

I show my recipe from Step 1 through Step 19.

I call out:

- event duplicate check;
- lifecycle data table;
- Java validation call;
- NetSuite before the monitor block;
- only the Zendesk in-progress update and Zendesk HTTP call inside the monitor block;
- a PII-safe alert after persistent Zendesk failure;
- `NEEDS_ATTENTION` failure update;
- `PROVISIONED` success update.

## 4. Happy path

I run `HappyPath` and show:

- successful Workato job;
- one NetSuite call;
- one Zendesk call;
- populated downstream IDs;
- `PROVISIONED` table state.

## 5. Required Saga path

I run `ZendeskTransientRecovery` and show:

- first Zendesk attempt returns 500;
- Workato retries the monitored Zendesk action;
- the second attempt succeeds;
- NetSuite ran only once;
- final state is `PROVISIONED`.

I use the correlation ID to connect the job, HTTP calls, and lifecycle row.

## 6. Production considerations

I briefly cover:

- durable queue and five-request NetSuite worker;
- Sunday outage backlog and recovery;
- Datadog/Splunk correlation search;
- Workato Connections and secret rotation;
- PII-safe logs;
- read-only provisioning status MCP tool for Slack.

I end by identifying which pieces are working prototypes and which are production architecture proposals.
