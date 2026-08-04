# Mock Downstream Systems

This small Node.js service replaces unavailable NetSuite and Zendesk sandboxes. It gives Workato real HTTP behavior, deterministic IDs, idempotent replays, and a controlled Zendesk failure for the saga demonstration.

## Endpoints

- `GET /health` — service health.
- `POST /reset` — clears in-memory test state.
- `GET /state` — shows non-sensitive counters and generated IDs.
- `POST /netsuite/customers` — creates or replays a mock NetSuite customer.
- `POST /zendesk/organizations` — creates or replays a mock Zendesk organization.
- `POST /alerts/provisioning` — records a PII-safe operational alert.

Both creation endpoints use `Idempotency-Key`. Repeating the same operation returns the original generated ID with `replayed: true`.

The Zendesk request accepts `simulateTransientFailure: true`. Its first attempt returns HTTP 500, allowing Workato to prove that only the monitored Zendesk actions are retried.

## Local verification

```powershell
node --test mock-systems.test.mjs
node mock-systems.mjs
```

The default address is `http://localhost:3001`.
