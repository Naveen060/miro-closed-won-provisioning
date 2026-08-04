# Java Microservice, Workato, and Mock Server Execution Guide

## 1. Purpose of this guide

This guide explains the implementation as one connected system. It answers four practical questions:

1. What does every Java file in the repository do?
2. How does the Workato recipe call the Java validation service?
3. What input enters each component and what output leaves it?
4. How does the Railway mock server represent NetSuite and Zendesk?

The important separation is:

- Workato owns orchestration and lifecycle state.
- The Java service owns order validation and validation idempotency.
- The mock server represents unavailable NetSuite and Zendesk systems.
- The Workato data table stores the durable status of each provisioning request.

## 2. End-to-end system overview

The runtime path is:

    Salesforce-style test event
        -> Workato webhook
        -> duplicate check in Provisioning Status v2
        -> Java validation service on Railway
        -> NetSuite mock endpoint on Railway
        -> Zendesk mock endpoint on Railway
        -> final Workato table update

The Java service and the mock server are separate applications:

- services/order-validation is the Spring Boot Java application.
- services/mock-systems is the Node.js mock application.

Railway is only the hosting platform. Railway does not automatically create customer IDs or validation results. The deployed source code creates those responses.

## 3. Workato recipe execution map

The current recipe contains 19 numbered steps. Logic blocks and error branches are included in the numbering.

| Step | Purpose | Main input | Main output or effect |
|---|---|---|---|
| 1 | Receive the Closed Won webhook | Salesforce-style JSON | Event, correlation, opportunity, account, customer, and test-control datapills |
| 2 | Search the lifecycle table | Step 1 event_id | Any existing record with that event ID |
| 3 | Decide whether the event is a duplicate | Step 2 Record ID | Duplicate or new-event branch |
| 4 | End a duplicate safely | Existing record found | Successful stop; no downstream calls |
| 5 | Create the lifecycle record | Step 1 webhook datapills | New table Record ID and initial RECEIVED state |
| 6 | Mark validation as pending | Step 5 Record ID | State becomes VALIDATION_PENDING |
| 7 | Call the Java validation service | Account and opportunity fields from Step 1 | Validation response datapills |
| 8 | Save validation result | Step 5 Record ID and Step 7 response | State becomes VALIDATED |
| 9 | Call the NetSuite mock | Account ID and validation result | customerId, correlationId, replayed |
| 10 | Save NetSuite result | Step 5 Record ID and Step 9 response | State becomes NETSUITE_CREATED |
| 11 | Mark Zendesk pending | Step 5 Record ID | State becomes ZENDESK_PENDING |
| 12 | Monitor the Zendesk unit of work | Steps 13 and 14 | Routes success or error to the correct branch |
| 13 | Mark an active Zendesk attempt | Step 5 Record ID | State becomes ZENDESK_IN_PROGRESS |
| 14 | Call the Zendesk mock | Account and NetSuite result | organizationId, correlationId, replayed |
| 15 | Retry the monitored work | Error from Step 13 or 14 | Up to three monitored attempts |
| 16 | Record persistent failure | Step 5 Record ID | State becomes NEEDS_ATTENTION and error metadata is stored |
| 17 | Send an operational alert | Safe event identifiers and failure metadata | Alert acceptance response from the mock alert endpoint |
| 18 | Stop the failed job | Persisted error context | Workato job ends as failed |
| 19 | Save successful completion | Step 5 Record ID and Step 14 response | State becomes PROVISIONED |

Step 19 must remain outside the persistent-error branch. It runs only when the monitored Zendesk work succeeds.

## 4. Webhook input contract

Step 1 receives a JSON document similar to this:

    {
      "event_id": "EVT-HAPPY-001",
      "correlation_id": "CORR-HAPPY-001",
      "event_type": "OPPORTUNITY_CLOSED_WON",
      "occurred_at": "2026-08-03T12:00:00Z",
      "source": "salesforce",
      "opportunity": {
        "opportunity_id": "OPP-001",
        "name": "Acme Enterprise Deal",
        "stage": "Closed Won",
        "close_date": "2026-08-03",
        "amount": 25000,
        "currency": "USD"
      },
      "account": {
        "account_id": "ACC-001",
        "name": "Acme Corp",
        "billing_country": "US"
      },
      "customer": {
        "admin_email": "provisioning-test@example.com"
      },
      "simulate_zendesk_failure": false
    }

Workato converts the declared webhook schema into datapills. A datapill is a typed reference to a value from an earlier step. It does not copy data by itself; the value is resolved when the job runs.

## 5. How Workato Step 7 calls the Java service

### Request

Step 7 sends an HTTP POST request to:

    /api/v1/orders/validate

The HTTP connection supplies the Railway base URL and the secret X-API-Key. The action supplies:

| Request part | Value source |
|---|---|
| X-API-Key | Secured Workato HTTP connection |
| X-Correlation-Id | Step 1 correlation_id |
| Idempotency-Key | Opportunity ID plus a validation suffix |
| accountId | Step 1 account.account_id |
| totalAmount | Step 1 opportunity.amount |
| currency | Step 1 opportunity.currency |
| countryCode | Step 1 account.billing_country |
| opportunityId | Step 1 opportunity.opportunity_id |

The body sent to Java is:

    {
      "accountId": "ACC-001",
      "totalAmount": 25000,
      "currency": "USD",
      "countryCode": "US",
      "opportunityId": "OPP-001"
    }

### Successful response

The Java service returns HTTP 200 with a body shaped like:

    {
      "accountId": "ACC-001",
      "validationStatus": "VALID",
      "totalAmount": 25000,
      "currency": "USD",
      "taxRoute": "US_DOMESTIC",
      "complianceChecks": [
        "REQUIRED_FIELDS_PRESENT",
        "SANCTIONS_SCREENING_REQUIRED",
        "TAX_ROUTE_SELECTED"
      ],
      "correlationId": "CORR-HAPPY-001",
      "processedAt": "2026-08-03T12:00:01Z"
    }

It also returns:

- X-Correlation-Id in the response headers.
- Idempotency-Replayed as false for the original request or true for a replay.

Workato's response schema tells the HTTP connector how to parse this runtime JSON. It creates datapills such as Validation status and Tax route. The schema does not generate the values. The Java response generates them.

### Error response

Invalid input produces a structured response such as:

    {
      "code": "VALIDATION_ERROR",
      "message": "Request validation failed",
      "details": ["accountId must not be blank"],
      "correlationId": "CORR-ERROR-001",
      "timestamp": "2026-08-03T12:00:01Z"
    }

The action fails on non-2xx status codes, which prevents NetSuite and Zendesk from running with invalid data.

## 6. Java repository structure

    services/order-validation/
      pom.xml
      mvnw
      mvnw.cmd
      src/main/resources/application.yml
      src/main/java/com/miro/provisioning/
        OrderValidationApplication.java
        api/
          OrderValidationController.java
          ApiExceptionHandler.java
        config/
          SecurityProperties.java
          CorrelationIdFilter.java
          ApiKeyFilter.java
        domain/
          OrderValidationRequest.java
          OrderValidationResponse.java
          ApiError.java
        service/
          OrderValidator.java
          DefaultOrderValidator.java
          OrderValidationService.java
          RequestFingerprint.java
          InMemoryIdempotencyService.java
          IdempotentResult.java
          InvalidIdempotencyKeyException.java
          IdempotencyConflictException.java
      src/test/java/com/miro/provisioning/
        OrderValidationApiTest.java
        service/InMemoryIdempotencyServiceTest.java

## 7. Every Java file at a glance

| File | Called by | Input | Output | Connection to Workato |
|---|---|---|---|---|
| OrderValidationApplication.java | Java runtime | Process arguments and configuration | Running Spring application | Makes the Step 7 API available |
| OrderValidationController.java | Spring MVC | Step 7 headers and JSON | HTTP response | Direct HTTP boundary used by Step 7 |
| ApiExceptionHandler.java | Spring MVC after an exception | Validation, parsing, conflict, or server exception | Structured ApiError | Gives Workato useful non-2xx errors |
| SecurityProperties.java | Spring configuration | app.security.api-key | Typed security setting | Defines the key Step 7 must send |
| CorrelationIdFilter.java | Servlet container | X-Correlation-Id header | Valid correlation ID in MDC and response | Preserves the Step 1 trace ID |
| ApiKeyFilter.java | Servlet container | X-API-Key header | Continue or HTTP 401 | Authenticates Workato before validation |
| OrderValidationRequest.java | Jackson and Bean Validation | Step 7 JSON | Valid Java request object | Defines accepted Step 7 fields |
| OrderValidationResponse.java | Validator and Jackson | Validated values | Step 7 JSON response | Defines the datapills used by Step 8 |
| ApiError.java | Exception handler and filters | Error information | Standard error JSON | Makes Workato failures consistent |
| OrderValidator.java | OrderValidationService | Request and correlation ID | OrderValidationResponse | Interface separating orchestration from rules |
| DefaultOrderValidator.java | OrderValidationService | Valid request | Validation decision | Produces validationStatus, taxRoute, checks |
| OrderValidationService.java | Controller | Request, key, correlation ID | IdempotentResult | Coordinates fingerprinting, caching, and validation |
| RequestFingerprint.java | OrderValidationService | Request object | SHA-256 fingerprint | Detects reuse of one key with different data |
| InMemoryIdempotencyService.java | OrderValidationService | Key, fingerprint, operation | Original or replayed result | Stops duplicate validation execution |
| IdempotentResult.java | Idempotency service | Value and replay flag | Result wrapper | Lets controller set Idempotency-Replayed |
| InvalidIdempotencyKeyException.java | Idempotency service | Invalid key condition | Typed exception | Becomes HTTP 400 for Workato |
| IdempotencyConflictException.java | Idempotency service | Key reused with changed input | Typed exception | Becomes HTTP 409 for Workato |
| OrderValidationApiTest.java | Maven test runner | HTTP test requests | Assertions | Verifies Step 7's external contract |
| InMemoryIdempotencyServiceTest.java | Maven test runner | Concurrent operations | Assertions | Verifies one execution and safe retry behavior |

## 8. Detailed Java execution sequence

For one Step 7 request, the classes execute in this order:

1. CorrelationIdFilter reads X-Correlation-Id.
2. ApiKeyFilter authenticates X-API-Key.
3. Spring MVC routes the request to OrderValidationController.
4. Jackson converts JSON into OrderValidationRequest.
5. Bean Validation checks required fields and the minimum amount.
6. OrderValidationController reads Idempotency-Key and calls OrderValidationService.
7. OrderValidationService asks RequestFingerprint for a deterministic hash.
8. OrderValidationService calls InMemoryIdempotencyService.
9. For a new request, InMemoryIdempotencyService invokes DefaultOrderValidator.
10. DefaultOrderValidator builds OrderValidationResponse.
11. IdempotentResult carries the response and replay indicator to the controller.
12. The controller returns JSON and response headers.
13. Workato parses the JSON into Step 7 datapills.
14. Step 8 writes the validation result to the Step 5 lifecycle record.

## 9. Detailed Java file reference

### OrderValidationApplication.java

This is the application entry point. Its main method starts Spring Boot, scans the application packages, creates controllers, filters, services, and configuration objects, and starts the embedded HTTP server. It contains no business validation. Workato can reach Step 7 only after this application is running on Railway.

### OrderValidationController.java

This class exposes POST /api/v1/orders/validate. Spring injects OrderValidationService into it. The controller accepts the Idempotency-Key header and a validated OrderValidationRequest. It obtains the correlation ID established by the filter, calls the service, returns HTTP 200, and sets Idempotency-Replayed. It should remain thin because validation and idempotency belong in service classes.

### ApiExceptionHandler.java

This class converts Java exceptions into predictable HTTP errors. Bean-validation problems and malformed JSON become HTTP 400. Reusing an idempotency key for changed data becomes HTTP 409. Unexpected failures become HTTP 500 without exposing a stack trace or customer PII. Every error contains the current correlation ID so the same job can be located in logs.

### SecurityProperties.java

This configuration object reads app.security.api-key. In Railway, the value comes from an environment variable. The property is required and nonblank. Workato stores the matching secret in its secured HTTP connection rather than inside the recipe JSON.

### CorrelationIdFilter.java

This filter executes first. It accepts X-Correlation-Id only when it matches the permitted character pattern and length. Otherwise, it creates a UUID. It writes the ID to the logging MDC and the response header, then removes it after the request completes. This is why Java logs and Workato can share one trace identifier.

### ApiKeyFilter.java

This filter protects API paths. It reads X-API-Key and compares it with the configured value using a constant-time comparison. An invalid or missing key returns HTTP 401 with ApiError. A valid key allows the request to continue to the controller.

### OrderValidationRequest.java

This immutable request model describes the JSON accepted from Workato. accountId must be present. totalAmount must be present and at least 0.01. currency, countryCode, and opportunityId are optional at the Java type level. Jackson binds JSON property names to this object, and Bean Validation evaluates its annotations before the controller runs.

### OrderValidationResponse.java

This response model defines the successful JSON returned to Workato. Its fields are accountId, validationStatus, totalAmount, currency, taxRoute, complianceChecks, correlationId, and processedAt. Workato's Step 7 response schema should use matching names and types.

### ApiError.java

This is the standard error model. code supports machine classification, message gives a safe summary, details contains field-level information, correlationId supports tracing, and timestamp records when the failure was produced.

### OrderValidator.java

This is the business-rule interface. It accepts an OrderValidationRequest and a correlation ID and returns OrderValidationResponse. The interface makes the rules replaceable and independently testable.

### DefaultOrderValidator.java

This is the current rule implementation. It normalizes currency and country values, defaulting them to USD and US when absent. It produces VALID status, selects a tax route, and records compliance checks. US, Canada, Great Britain, and Germany receive named tax routes; other countries use INTERNATIONAL_REVIEW. Orders of at least 1,000,000 receive an additional high-value review check.

### OrderValidationService.java

This is the application service called by the controller. It creates a request fingerprint, delegates duplicate protection to InMemoryIdempotencyService, and invokes OrderValidator only when execution is required. Its logs use safe identifiers instead of full request bodies, which reduces PII exposure.

### RequestFingerprint.java

This class serializes the request deterministically and calculates a SHA-256 hexadecimal hash. The fingerprint distinguishes an exact retry from a dangerous request that reused the same idempotency key with different input.

### InMemoryIdempotencyService.java

This service stores results in a thread-safe map. A new key starts one operation. Concurrent callers with the same key and fingerprint wait for and reuse the same result. A later exact retry receives the cached response with replayed set to true. The same key with another fingerprint throws a conflict. A failed operation is removed so a legitimate retry can execute again.

The store is intentionally suitable for this assessment, but it is memory-only. A production deployment should replace it with Redis or a database so replay protection survives restarts and works across multiple application instances.

### IdempotentResult.java

This record wraps the response value and a boolean replayed flag. It lets the controller return the same response body while accurately setting the Idempotency-Replayed header.

### InvalidIdempotencyKeyException.java

This exception represents a blank, missing, or overly long idempotency key. ApiExceptionHandler converts it to HTTP 400.

### IdempotencyConflictException.java

This exception represents a key that was already associated with different request content. ApiExceptionHandler converts it to HTTP 409, preventing accidental processing of changed data as a retry.

### OrderValidationApiTest.java

This integration-style test exercises the HTTP contract. It verifies unauthorized requests, invalid bodies, a successful original request, an exact replay, and a conflict when the same idempotency key is used with changed content.

### InMemoryIdempotencyServiceTest.java

This unit test stresses the idempotency algorithm. It verifies that concurrent identical calls execute the operation once, failed operations are not cached permanently, and conflicting fingerprints are rejected.

## 10. Supporting Java project files

### pom.xml

The Maven build defines Java 21, Spring Boot, web, validation, actuator, and test dependencies. The compiler and test plugins turn the source into the deployable application and run automated tests. The Maven Enforcer rule prevents building with a Java version below 21.

### mvnw and mvnw.cmd

These are the Maven Wrapper launchers for Unix-like systems and Windows. They allow the project to use its expected Maven version without requiring a separate global Maven installation.

### application.yml

This file defines the application name, HTTP port, security property binding, actuator behavior, and the logging pattern containing the correlation ID. Railway overrides environment-dependent values such as PORT and the API key.

## 11. How the mock server works

### What it is

The mock server is the application in services/mock-systems/mock-systems.mjs. Railway deploys it and gives it a public HTTPS address. It is a controlled replacement for NetSuite and Zendesk because no real sandboxes are available.

It is similar in purpose to Mocky or webhook.site, but it gives this project more control:

- It creates deterministic fake NetSuite and Zendesk IDs.
- It implements idempotency.
- It can intentionally fail the first Zendesk call.
- It exposes state for test evidence.
- It accepts operational alerts.

It does not contact real NetSuite or Zendesk.

### Runtime state

The server keeps in-memory maps for:

- NetSuite customers by idempotency key.
- Zendesk organizations by idempotency key.
- Zendesk attempt counts.
- Provisioning alerts.

It also keeps endpoint call counters. This state is cleared when the process restarts or when POST /reset is called.

### Health and test-control endpoints

| Endpoint | Purpose |
|---|---|
| GET /health | Confirms the mock process is running |
| POST /reset | Clears IDs, attempts, alerts, and counters |
| GET /state | Returns counts and attempt information for test evidence |

### NetSuite mock endpoint

Workato Step 9 calls POST /netsuite/customers.

Required headers:

- Idempotency-Key, normally Opportunity ID plus a netsuite suffix.
- X-Correlation-Id from Step 1.

The request body contains the account identifier and validation status. The endpoint generates customerId itself. For the first unique key, it returns HTTP 201:

    {
      "customerId": "ns-0001",
      "correlationId": "CORR-HAPPY-001",
      "replayed": false
    }

If Workato sends the same key again, the server does not create another customer. It returns HTTP 200 with the original customerId and replayed true.

The data origin is therefore:

- accountId: Workato Step 1.
- validationStatus: Java response from Step 7.
- customerId: generated by mock-systems.mjs.
- correlationId: copied from the HTTP request header.
- replayed: calculated from the mock server's idempotency map.

Step 10 consumes these response datapills and updates the lifecycle table.

### Zendesk mock endpoint

Workato Step 14 calls POST /zendesk/organizations.

Required headers:

- Idempotency-Key, normally Opportunity ID plus a zendesk suffix.
- X-Correlation-Id from Step 1.

The body contains the account information, the NetSuite customer ID from Step 9, and the top-level simulate_zendesk_failure test flag.

On success it returns HTTP 201:

    {
      "organizationId": "zd-0001",
      "netSuiteCustomerId": "ns-0001",
      "correlationId": "CORR-HAPPY-001",
      "replayed": false
    }

The data origin is:

- account details: Step 1 webhook.
- netSuiteCustomerId: Step 9 mock response.
- organizationId: generated by mock-systems.mjs.
- correlationId: copied from the request header.
- replayed: calculated from the Zendesk idempotency map.

Step 19 consumes these values and stores PROVISIONED after success.

### Simulated Zendesk failure and Saga recovery

When simulate_zendesk_failure is true, the mock server returns HTTP 500 for the first attempt associated with that Zendesk idempotency key. The sequence is:

1. Step 9 creates the NetSuite customer successfully.
2. Step 10 permanently saves the NetSuite customer ID.
3. Step 14 makes the first Zendesk call and receives HTTP 500.
4. The monitor sends only Steps 13 and 14 to Step 15 retry handling.
5. Step 13 records another ZENDESK_IN_PROGRESS attempt.
6. Step 14 retries with the same Zendesk idempotency key.
7. The mock server recognizes that the forced first failure already occurred and creates the organization.
8. Step 19 saves PROVISIONED.

NetSuite is outside the monitor, so it is not called again. This is the required Saga behavior.

### Alert mock endpoint

Step 17 calls POST /alerts/provisioning after all Zendesk retries fail. The request carries safe identifiers and failure metadata, not customer email or the full payload. The mock stores the alert and returns HTTP 202 with accepted, alertId, correlationId, and state.

## 12. Where response-schema fields come from

The Workato response schema is a parsing contract. It tells Workato what properties to expect in an HTTP response. It neither calls the backend nor manufactures data.

For Step 9:

| Workato response field | Actual producer |
|---|---|
| customerId | NetSuite handler in mock-systems.mjs |
| correlationId | Mock handler copying X-Correlation-Id |
| replayed | Mock handler checking its in-memory map |

For Step 14:

| Workato response field | Actual producer |
|---|---|
| organizationId | Zendesk handler in mock-systems.mjs |
| netSuiteCustomerId | Mock handler copying the request value |
| correlationId | Mock handler copying X-Correlation-Id |
| replayed | Mock handler checking its in-memory map |

If the schema says customerId but the server returns customer_id, the Workato datapill will not be populated correctly. The schema and actual backend JSON must match exactly.

## 13. Value lineage across the full execution

| Value | Created by | Used by |
|---|---|---|
| event_id | Salesforce-style sender | Duplicate search, lifecycle table, alert |
| correlation_id | Sender, or generated when absent | Workato, Java logs, mock responses, alert |
| opportunity_id | Sender | Java request, idempotency keys, lifecycle table |
| account_id | Sender | Java request, NetSuite mock, Zendesk mock |
| validationStatus | DefaultOrderValidator | Step 8 and NetSuite request |
| validation replay flag | InMemoryIdempotencyService | Step 8 lifecycle metadata |
| customerId | NetSuite mock handler | Step 10, Zendesk request |
| NetSuite replay flag | NetSuite mock idempotency map | Step 10 lifecycle metadata |
| organizationId | Zendesk mock handler | Step 19 lifecycle record |
| Zendesk replay flag | Zendesk mock idempotency map | Step 19 lifecycle metadata |
| lifecycle Record ID | Workato Data Tables Step 5 | Every later table update |

## 14. Main execution scenarios

### Happy path

The event is new, validation succeeds, NetSuite returns a customer ID, Zendesk returns an organization ID, and Step 19 sets PROVISIONED.

### Duplicate event

Step 2 finds the existing event_id. Step 3 takes the duplicate branch and Step 4 stops successfully. Java, NetSuite, and Zendesk are not called again.

### Invalid order

The Java request fails Bean Validation at Step 7. Workato receives HTTP 400 and does not call NetSuite or Zendesk.

### Zendesk transient failure

NetSuite succeeds once. Zendesk returns one forced HTTP 500, the monitor retries only the Zendesk unit, and the final state becomes PROVISIONED.

### Persistent Zendesk failure

After retries are exhausted, Step 16 sets NEEDS_ATTENTION, Step 17 sends an alert, and Step 18 ends the Workato job as failed.

## 15. Automated verification

The Java tests are run from services/order-validation with:

    .\mvnw.cmd test

The mock tests are run from services/mock-systems with:

    node --test mock-systems.test.mjs

Together they verify the Java API contract, authentication, validation, idempotency, mock ID generation, replay behavior, transient Zendesk failure, alert handling, and state inspection.

## 16. Production considerations

The implementation demonstrates the required integration behavior, but the following assessment components are intentionally simplified:

- Java idempotency is in memory; production should use Redis or a database.
- Mock server state is in memory; it represents downstream systems and is not a durable database.
- Real NetSuite and Zendesk authentication and API contracts would replace the mock endpoints.
- Workato connections should store secrets and restrict access by environment.
- Logs should contain IDs, states, and error categories, not full request bodies or customer email.
- A production queue should buffer NetSuite work, enforce a maximum concurrency of five, and pause delivery during the Sunday outage window.
- A scheduled reconciliation recipe should find stale nonterminal lifecycle records and safely resume or alert them.
- A read-only status API or MCP tool can query the lifecycle table for Slack without exposing PII.
