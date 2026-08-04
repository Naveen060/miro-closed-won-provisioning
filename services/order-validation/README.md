# Order Validation Service

This Java 21 Spring Boot service is the validation hop called by Workato before provisioning begins.

## API contract

`POST /api/v1/orders/validate`

Required headers:

- `X-API-Key` authenticates Workato.
- `Idempotency-Key` identifies the validation operation.
- `X-Correlation-Id` traces the request across the workflow.

Request:

```json
{
  "accountId": "ACC-1001",
  "totalAmount": 25000,
  "currency": "USD",
  "countryCode": "US",
  "opportunityId": "OPP-1001"
}
```

Response:

```json
{
  "validationId": "VAL-...",
  "status": "VALIDATED",
  "accountId": "ACC-1001",
  "replayed": false
}
```

## Code responsibilities

- `api` owns HTTP input, output, and consistent error responses.
- `config` owns API-key enforcement and correlation-ID propagation.
- `domain` defines the request, response, and error contracts.
- `service` owns validation, request fingerprinting, and idempotent replay.

The controller stays thin. Business rules and replay behavior remain testable without starting the web layer.

## Local verification

```powershell
.\mvnw.cmd clean verify
```

Run the service:

```powershell
$env:VALIDATION_API_KEY = "local-validation-key"
.\mvnw.cmd spring-boot:run
```

Blank account IDs and missing, zero, or negative amounts return HTTP 400. Currency values are normalized to uppercase; this exercise does not implement a currency allow-list.
