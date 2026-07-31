# Workato tests

I use the PowerShell runner to create nested webhook payloads that match my current Workato trigger schema. Each run uses unique identifiers.

## Preview without using credits

```powershell
.\workato\scripts\test-workflow.ps1 `
  -PreviewOnly `
  -Cases HappyPath,ZendeskTransientRecovery
```

## Run the two main cases

Put the Workato recipe in Test mode or start it. Then run:

```powershell
$env:WORKATO_WEBHOOK_URL = "https://webhooks.example/replace-me"

.\workato\scripts\test-workflow.ps1 `
  -Cases HappyPath,ZendeskTransientRecovery `
  -ResetMockState
```

I intentionally keep the webhook URL out of Git.

## Cases

| Case | Expected result |
|---|---|
| `HappyPath` | Validation, NetSuite, and Zendesk succeed |
| `ZendeskTransientRecovery` | Zendesk fails once and then succeeds |
| `DuplicateFirst` | First event is processed |
| `DuplicateReplay` | Repeated event is stopped as a duplicate |
| `MissingAccountId` | Java validation rejects the request |
| `MissingTotalAmount` | Java validation rejects the request |
| `ZeroAmount` | Java validation rejects the request |
| `ConflictSeed` | First idempotent validation succeeds |
| `ConflictChanged` | Same validation key with different input returns conflict |

Run paired cases together:

```powershell
.\workato\scripts\test-workflow.ps1 -Cases DuplicateFirst,DuplicateReplay
.\workato\scripts\test-workflow.ps1 -Cases ConflictSeed,ConflictChanged
```

## Evidence I collect

For the happy path, I verify:

- job result is Successful;
- Step 17 ran;
- lifecycle state is `PROVISIONED`;
- NetSuite and Zendesk IDs are stored.

For transient recovery, I verify:

- Java ran once;
- NetSuite ran once;
- the first Zendesk request returned HTTP 500;
- Workato retried Step 13;
- Zendesk later succeeded;
- final state is `PROVISIONED`.

For duplicate replay, I verify that Java, NetSuite, and Zendesk do not run during the second event.

I save the generated JSON report under `test-results`. Git ignores these generated reports.
