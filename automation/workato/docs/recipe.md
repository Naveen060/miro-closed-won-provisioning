# Workato recipe

This document matches the recipe I built in Workato.

## Assets

- Recipe: `Closed-Won Provisioning Orchestration v2`
- Data table: `Provisioning Status v2`
- Validation connection: `Order Validation HTTP Secure v2`
- Mock connection: `Provisioning Mock Systems v2`

I use the connections to hold the base URLs and `X-API-Key`. I do not enter secrets in recipe formulas.

## Webhook schema

```json
{
  "event_id": "EVT-1001",
  "correlation_id": "CORR-1001",
  "event_type": "OPPORTUNITY_CLOSED_WON",
  "occurred_at": "2026-07-30T20:00:00Z",
  "opportunity": {
    "opportunity_id": "OPP-1001",
    "name": "Acme Enterprise Deal",
    "stage": "Closed Won",
    "close_date": "2026-07-30",
    "amount": 75000,
    "currency": "USD"
  },
  "account": {
    "account_id": "ACC-1001",
    "name": "Acme Corp",
    "billing_country": "US"
  },
  "customer": {
    "admin_email": "integration-test@example.test"
  },
  "source": "salesforce",
  "simulate_zendesk_failure": false
}
```

## Data-table purpose

I use one row for each Closed Won event. The table stores the business state across Workato, Java, NetSuite, and Zendesk.

Important columns include:

- `event_id`
- `opportunity_id`
- `account_id`
- `account_name`
- `correlation_id`
- `state`
- `validation_id`
- `netsuite_customer_id`
- `zendesk_organization_id`
- replay flags
- retry and error fields
- created and updated timestamps

## Recipe steps

| Step | Action | Main configuration |
|---:|---|---|
| 1 | HTTP webhook trigger | Receive the nested payload above |
| 2 | Search `Provisioning Status v2` | `event_id` equals Step 1 Event ID; limit 1 |
| 3 | IF record is present | Detect a duplicate event |
| 4 | Stop job successfully | Duplicate event is ignored |
| 5 | Create data-table record | Map the Step 1 event, opportunity, account, and correlation fields |
| 6 | Update record | Record ID from Step 5; state `VALIDATION_PENDING` |
| 7 | HTTP validation | `POST /api/v1/orders/validate` |
| 8 | Update record | State `VALIDATED`; store validation response |
| 9 | HTTP NetSuite mock | `POST /netsuite/customers` |
| 10 | Update record | State `NETSUITE_CREATED`; store customer ID and replay flag |
| 11 | Update record | State `ZENDESK_PENDING` |
| 12 | Handle errors | Monitor only Steps 13 and 14; NetSuite remains outside this boundary |
| 13 | Update record | State `ZENDESK_IN_PROGRESS` before each monitored attempt |
| 14 | HTTP Zendesk mock | `POST /zendesk/organizations` |
| 15 | Retry | Retry monitored Steps 13 and 14 up to 3 times, 2 seconds apart |
| 16 | Update persistent failure | State `NEEDS_ATTENTION`; save retry and safe error fields |
| 17 | HTTP failure alert | `POST /alerts/provisioning` with identifiers and safe error metadata only |
| 18 | Stop job as failed | Explain that Zendesk failed after retries and the failure was persisted |
| 19 | Update successful result | State `PROVISIONED`; store Zendesk organization ID |

Step 19 is outside the persistent-error branch. When it finishes, Workato ends the job successfully automatically.

## Step 7: Java validation

```text
Method: POST
Path: /api/v1/orders/validate
Content type: Raw JSON
Idempotency-Key: <Step 1 opportunity_id>:validation
X-Correlation-Id: <Step 1 correlation_id>
Mark non-2xx as success: No
```

Body:

```json
{
  "accountId": "<Step 1 account.account_id>",
  "totalAmount": "<Step 1 opportunity.amount>",
  "currency": "<Step 1 opportunity.currency>",
  "countryCode": "<Step 1 account.billing_country>",
  "opportunityId": "<Step 1 opportunity.opportunity_id>"
}
```

## Step 9: NetSuite mock

```text
Method: POST
Path: /netsuite/customers
Idempotency-Key: <opportunity_id>:netsuite
X-Correlation-Id: <correlation_id>
```

I keep NetSuite outside the Zendesk monitor so a Zendesk retry cannot rerun it.

## Step 14: Zendesk mock

```text
Method: POST
Path: /zendesk/organizations
Idempotency-Key: <opportunity_id>:zendesk
X-Correlation-Id: <correlation_id>
Mark non-2xx as success: No
Wait for response: No
```

Body:

```json
{
  "accountId": "<Step 1 account.account_id>",
  "netSuiteCustomerId": "<Step 9 customerId>",
  "simulateTransientFailure": <Step 1 simulate_zendesk_failure>
}
```

I leave the Boolean datapill outside quotation marks so Workato sends a Boolean rather than a string.

Response schema:

```json
{
  "organizationId": "zd-0001",
  "netSuiteCustomerId": "ns-0001",
  "correlationId": "CORR-1001",
  "replayed": false
}
```

## Step 17: Persistent-failure alert

```text
Method: POST
Path: /alerts/provisioning
Connection: Provisioning Mock Systems v2
Content type: Raw JSON
Mark non-2xx as success: No
```

```json
{
  "eventId": "<Step 1 event_id>",
  "correlationId": "<Step 1 correlation_id>",
  "opportunityId": "<Step 1 opportunity.opportunity_id>",
  "state": "NEEDS_ATTENTION",
  "failedStep": "ZENDESK_CREATE",
  "errorCategory": "DOWNSTREAM_5XX",
  "retryCount": 3
}
```

I exclude account names, email addresses, and other PII from this alert.

## Final states

```text
RECEIVED              lifecycle record created
VALIDATION_PENDING    waiting for Java validation
VALIDATED             validation completed
NETSUITE_CREATED       NetSuite mock completed
ZENDESK_PENDING        waiting to start Zendesk provisioning
ZENDESK_IN_PROGRESS    a monitored Zendesk attempt is running
PROVISIONED            all required actions completed
NEEDS_ATTENTION        Zendesk still failed after retries
```

I allow the successful path to end automatically after Step 19. I end the persistent failure path explicitly at Step 18 after recording the failure and sending the alert.
