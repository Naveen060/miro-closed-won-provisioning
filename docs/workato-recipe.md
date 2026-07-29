# Workato recipe build sheet

This is the preferred-platform version of the executable n8n workflow and local runner. It uses only the Webhooks and HTTP connectors, so no NetSuite or Zendesk sandbox is required.

## Connections and properties

Create HTTP connections for:

- validation service base URL;
- NetSuite mock base URL; and
- Zendesk mock base URL.

Store `VALIDATION_API_KEY` in the validation HTTP connection or the workspace secret manager. Do not put it in a formula or lookup table. For a local service, expose ports 8080 and 8081 through an approved development tunnel because Workato cannot call `localhost` on the candidate's machine.

## Trigger sample

Choose **Webhooks by Workato -> New event via HTTP webhook** and use this JSON sample to define the schema:

```json
{
  "opportunityId": "opp-456",
  "accountId": "acct-123",
  "accountName": "Acme Corp",
  "totalAmount": 12500.50,
  "currency": "USD",
  "countryCode": "US",
  "correlationId": "47c2ba99-a18f-4e2b-933c-b68ef1b3ad09",
  "simulateZendeskFailure": false
}
```

The demo treats `correlationId` as a Salesforce-generated field. If it is absent, add a **Set variable** step and generate one before making calls.

## Recipe steps

### 1. Validate order

Add **HTTP -> Send request**:

```text
Method: POST
Path: /api/v1/orders/validate
Content-Type: application/json
X-API-Key: connection secret
Idempotency-Key: <opportunityId>:validation
X-Correlation-Id: <correlationId>
```

Map `accountId`, `totalAmount`, `currency`, `countryCode`, and `opportunityId` into the JSON body. Stop the job as failed for HTTP 400/401/409; those are not transient.

### 2. Create the NetSuite customer stub

Add **HTTP -> Send request** before any error-monitor block:

```text
Method: POST
Path: /netsuite/customers
Idempotency-Key: <opportunityId>:netsuite
X-Correlation-Id: <correlationId>
```

Map the account ID and validation status. Capture `customerId` from the response.

### 3. Create a Zendesk-only retry boundary

Add **Handle errors**. Configure the **Monitor actions for error** block with exactly one action: the Zendesk HTTP call.

```text
Method: POST
Path: /zendesk/organizations
Idempotency-Key: <opportunityId>:zendesk
X-Correlation-Id: <correlationId>
Body: accountId, NetSuite customerId, simulateTransientFailure
```

Configure the error block:

```text
Retry actions in Monitor block: 3
Time interval between retries: 2 seconds
Retry IF: HTTP status is 500-599 (when the status datapill is available)
```

The critical placement is NetSuite outside the monitor block. Workato retries the monitored action, so a Zendesk 500 does not execute NetSuite again. The mock also honors its own step-scoped idempotency key, making a manual replay of the entire Workato job safe.

After retries are exhausted, write a redacted event to the Workato Logging Service with correlation ID, opportunity ID, failed step, stable error category, and retry count. Then **Stop job** as failed so RecipeOps can alert. Never log the customer body, connection headers, or API key.

### 4. Record completion

After the Handle errors block, record `PROVISIONED`, NetSuite customer ID, Zendesk organization ID, correlation ID, and completion time. In production this is a durable saga-state update. In the prototype, the Workato job output is sufficient.

## Test cases

### Happy path

Post the trigger sample with `simulateZendeskFailure: false`. Verify one green execution for validation, NetSuite, and Zendesk.

### Required saga path

Post a new opportunity with `simulateZendeskFailure: true`. In job detail, verify:

- validation ran once;
- NetSuite ran once;
- Zendesk attempt 1 returned HTTP 500;
- Workato retried Zendesk;
- Zendesk attempt 2 returned success; and
- the job finished as provisioned.

The current Workato documentation describes a maximum of three retries and a one-to-ten-second interval for a Handle errors block: [Error handling best practices](https://docs.workato.com/recipes/best-practices-error-handling). The HTTP action and secret-storage behavior are documented under [Send request via HTTP](https://docs.workato.com/en/developing-connectors/http/building-http-action).

