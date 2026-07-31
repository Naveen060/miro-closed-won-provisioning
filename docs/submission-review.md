# Submission checklist

## Implemented

- [x] Java 21 Spring Boot service
- [x] `POST /api/v1/orders/validate`
- [x] API-key authentication
- [x] required-field validation
- [x] strict in-memory idempotency
- [x] concurrent duplicate safety
- [x] NetSuite and Zendesk mocks
- [x] transient Zendesk failure simulation
- [x] nested Workato webhook schema
- [x] duplicate-event check
- [x] lifecycle data table
- [x] Zendesk-only Workato retry boundary
- [x] success and persistent-failure states
- [x] Workato test harness
- [x] production design answers

## Verified locally

```text
Java tests: 7 passed, 0 failed
Maven result: BUILD SUCCESS
Local Saga: NetSuite 1 call, Zendesk 2 calls
Zendesk attempt 1: HTTP 500
Zendesk attempt 2: HTTP 201
```

## Verify before sending

- [ ] Workato happy-path job is Successful
- [ ] Workato transient Zendesk job is Successful after retry
- [ ] Mock state proves NetSuite ran once in the retry case
- [ ] Duplicate webhook stops before Java and downstream calls
- [ ] Lifecycle table contains the correct final states and IDs
- [ ] Screenshots contain no credentials or private webhook URL
- [ ] Git history contains no secrets
- [ ] README commands work from a clean clone
- [ ] Demonstration video is five to ten minutes
- [ ] Repository link is accessible to the reviewer
