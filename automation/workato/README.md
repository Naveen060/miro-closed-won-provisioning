# Workato Recipe Contract

The main recipe is `Closed-Won Provisioning Orchestration v2`. It coordinates validation, mock NetSuite provisioning, mock Zendesk provisioning, retries, lifecycle state, and alerts.

## Step map

| Step | Action | Purpose |
|---:|---|---|
| 1 | HTTP webhook | Receives the Salesforce-style Closed Won event. |
| 2 | Search lifecycle table | Looks up `event_id` for idempotency. |
| 3 | IF record exists | Routes duplicates away from provisioning. |
| 4 | Stop successfully | Ends a duplicate without repeating downstream calls. |
| 5 | Create lifecycle record | Creates the durable record in state `RECEIVED`. |
| 6 | Update record | Sets `VALIDATION_PENDING`. |
| 7 | Validation HTTP call | Calls the Java API. |
| 8 | Update record | Saves the validation response and `VALIDATED`. |
| 9 | NetSuite mock HTTP call | Creates the mock customer once. |
| 10 | Update record | Saves `customerId`, replay status, and `NETSUITE_CREATED`. |
| 11 | Update record | Sets `ZENDESK_PENDING`. |
| 12 | Error monitor | Contains only Steps 13 and 14. |
| 13 | Update record | Sets `ZENDESK_IN_PROGRESS` before every attempt. |
| 14 | Zendesk mock HTTP call | Creates the organization; may return a controlled 500. |
| 15 | Retry | Retries monitored Steps 13–14 up to three times. |
| 16 | Update record | Persists `NEEDS_ATTENTION` after retries are exhausted. |
| 17 | Alert HTTP call | Sends a PII-safe operational alert. |
| 18 | Stop with error | Ends the persistent-failure path. |
| 19 | Update record | Saves the Zendesk result and sets `PROVISIONED`. |

## Non-negotiable mappings

- Step 5 is populated from Step 1 webhook datapills, never Step 2 search results.
- Step 5 sets `state = RECEIVED` and is the only step that sets `created_at`.
- Every update after Step 5 uses the Step 5 `Record ID`.
- Update steps set only `updated_at` with a valid date/time datapill.
- Step 8 uses the Step 7 response.
- Step 10 uses the Step 9 response.
- Step 19 is outside the persistent-error branch.

## HTTP conventions

Every service call passes `X-Correlation-Id`. Creation calls also pass an operation-specific `Idempotency-Key`, for example `event_id:netsuite` and `event_id:zendesk`.

The response schema in Workato is a parsing contract. It does not generate data. The Java or mock server returns JSON, and Workato uses the configured schema to expose response fields as datapills.

## Test control

The webhook field `simulate_zendesk_failure` is only a test switch. `false` runs the happy path. `true` makes the mock Zendesk endpoint fail its first attempt so the retry behavior can be demonstrated. It is not part of a real production Salesforce event.

An example payload is in `schemas/closed-won-event.example.json`. Local test commands are in `tests/test-workflow.ps1`.
