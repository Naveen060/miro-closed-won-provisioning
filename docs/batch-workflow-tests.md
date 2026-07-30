# Batch testing the Workato workflow

The PowerShell runner sends a collection of test events to the Workato webhook. It runs them sequentially so that the duplicate and idempotency-conflict pairs remain deterministic and the Workato job history is easy to inspect.

## Before running

1. Save the recipe and either start it or put it in Workato **Test** mode so the webhook is listening.
2. Open PowerShell in the repository folder:

```powershell
cd "C:\Users\chava\OneDrive\Documents\miro"
```

## Preview without using Workato credits

```powershell
.\scripts\test-workato-workflow.ps1 -PreviewOnly
```

This prints every payload and creates a report, but sends no HTTP requests.

## Run the complete suite

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\test-workato-workflow.ps1 -ResetMockState
```

The complete suite submits nine Workato jobs. `-ResetMockState` clears only the NetSuite/Zendesk mock data; it does not clear the separate validator cache. Each run therefore creates unique identifiers automatically.

## Run only selected cases

Use this when you want to conserve credits:

```powershell
.\scripts\test-workato-workflow.ps1 -Cases HappyPath,ZendeskTransientRecovery
```

Available names are:

- `HappyPath`
- `ZendeskTransientRecovery`
- `DuplicateFirst`
- `DuplicateReplay`
- `MissingAccountId`
- `MissingTotalAmount`
- `ZeroAmount`
- `ConflictSeed`
- `ConflictChanged`

Run dependent cases together and in their listed order:

```powershell
.\scripts\test-workato-workflow.ps1 -Cases DuplicateFirst,DuplicateReplay
.\scripts\test-workato-workflow.ps1 -Cases ConflictSeed,ConflictChanged
```

## How to evaluate the run

The webhook normally returns `status: ok`. That means Workato accepted the event; it does not prove the entire recipe succeeded.

After the runner finishes:

1. Open the recipe's **Jobs** page.
2. Open each job and match it to the `correlationId` shown by the runner.
3. Compare the Workato result to `expectedFinalResult` and `expectedEvidence` in the saved report.
4. Send back the Workato result or screenshot for every case.

Reports are written to:

```text
test-results\workato-suite-YYYYMMDD-HHMMSS.json
```

The report also captures the mock system state before and after the suite, which helps verify downstream record creation, replay behavior, and Zendesk retry attempts.
