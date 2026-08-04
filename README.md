# Closed-Won Provisioning Orchestration

I built this project to demonstrate how a Salesforce Closed Won event can be validated and provisioned through Workato without creating duplicate NetSuite or Zendesk records.

The solution has three runtime parts:

- `services/order-validation` — Java 21/Spring Boot validation and idempotency API.
- `services/mock-systems` — local HTTP substitutes for NetSuite, Zendesk, and operational alerts.
- `automation/workato` — the 19-step Workato recipe contract, payload schema, and local test launcher.

Supporting production and submission notes are kept in `docs`.

## Repository map

```text
.
├── services/
│   ├── order-validation/   Java validation microservice
│   └── mock-systems/       NetSuite, Zendesk, and alert mocks
├── automation/
│   └── workato/
│       ├── README.md       Exact recipe behavior and field mappings
│       ├── schemas/        Example Salesforce webhook payload
│       └── tests/          PowerShell workflow test launcher
├── docs/
│   ├── production-readiness.md
│   └── demo-and-submission.md
├── Dockerfile              Railway entry point for the Java service
└── .env.example            Local configuration template
```

## Run locally

Java validation service:

```powershell
cd services\order-validation
.\mvnw.cmd clean verify
$env:VALIDATION_API_KEY = "local-validation-key"
.\mvnw.cmd spring-boot:run
```

Mock downstream systems, in a second terminal:

```powershell
node --test services\mock-systems\mock-systems.test.mjs
node services\mock-systems\mock-systems.mjs
```

The Java health endpoint is `http://localhost:8080/actuator/health`. The mock-service health endpoint is `http://localhost:3001/health`.

## Main behavior

1. Workato receives and persists the webhook before downstream processing.
2. It rejects a repeated `event_id` before calling any external system.
3. The Java API validates the order using an API key, idempotency key, and correlation ID.
4. NetSuite creation is simulated once and its result is saved.
5. Only Zendesk is placed inside the retry monitor, so a Zendesk 500 does not repeat NetSuite.
6. The lifecycle ends in `PROVISIONED`, or `NEEDS_ATTENTION` with a PII-safe alert.

See [automation/workato/README.md](automation/workato/README.md) for recipe mappings and [docs/production-readiness.md](docs/production-readiness.md) for the production design.

## Author

Venkata Naveen Chava
