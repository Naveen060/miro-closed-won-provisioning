[CmdletBinding()]
param(
    [string]$WebhookUrl = $env:WORKATO_WEBHOOK_URL,
    [string]$MockBaseUrl = "https://mock-systems-production.up.railway.app",
    [string[]]$Cases = @(),
    [ValidateRange(0, 60)]
    [int]$DelaySeconds = 3,
    [switch]$ResetMockState,
    [switch]$PreviewOnly
)

$Cases = @(
    $Cases |
        ForEach-Object { $_ -split "," } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)

if (-not $PreviewOnly -and [string]::IsNullOrWhiteSpace($WebhookUrl)) {
    throw "Set WORKATO_WEBHOOK_URL or pass -WebhookUrl before running live tests."
}

$ErrorActionPreference = "Stop"

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$allCaseNames = @(
    "HappyPath",
    "ZendeskTransientRecovery",
    "DuplicateFirst",
    "DuplicateReplay",
    "MissingAccountId",
    "MissingTotalAmount",
    "ZeroAmount",
    "ConflictSeed",
    "ConflictChanged"
)

if ($Cases.Count -eq 0) {
    $Cases = $allCaseNames
}

$unknownCases = @($Cases | Where-Object { $_ -notin $allCaseNames })
if ($unknownCases.Count -gt 0) {
    throw "Unknown test case(s): $($unknownCases -join ', '). Valid cases: $($allCaseNames -join ', ')"
}

$duplicateOpportunityId = "OPP-DUP-$runId"
$duplicateAccountId = "ACC-DUP-$runId"
$duplicateCorrelationId = "duplicate-$runId"
$conflictOpportunityId = "OPP-CONFLICT-$runId"
$conflictAccountId = "ACC-CONFLICT-$runId"

$testCases = @(
    [pscustomobject]@{
        Name = "HappyPath"
        ExpectedFinalResult = "Successful"
        ExpectedEvidence = "Validation, NetSuite, and Zendesk complete without retry."
        Payload = [ordered]@{
            opportunityId = "OPP-HAPPY-$runId"
            accountId = "ACC-HAPPY-$runId"
            accountName = "Happy Path Test"
            totalAmount = 15000
            currency = "USD"
            countryCode = "US"
            correlationId = "happy-$runId"
            simulateZendeskFailure = $false
        }
    },
    [pscustomobject]@{
        Name = "ZendeskTransientRecovery"
        ExpectedFinalResult = "Successful"
        ExpectedEvidence = "First Zendesk request fails; the monitor/retry block retries and later succeeds."
        Payload = [ordered]@{
            opportunityId = "OPP-ZENDESK-$runId"
            accountId = "ACC-ZENDESK-$runId"
            accountName = "Zendesk Retry Test"
            totalAmount = 15000
            currency = "USD"
            countryCode = "US"
            correlationId = "zendesk-retry-$runId"
            simulateZendeskFailure = $true
        }
    },
    [pscustomobject]@{
        Name = "DuplicateFirst"
        ExpectedFinalResult = "Successful"
        ExpectedEvidence = "Creates the first validation, NetSuite, and Zendesk result for the duplicate pair."
        Payload = [ordered]@{
            opportunityId = $duplicateOpportunityId
            accountId = $duplicateAccountId
            accountName = "Duplicate Replay Test"
            totalAmount = 20000
            currency = "USD"
            countryCode = "US"
            correlationId = $duplicateCorrelationId
            simulateZendeskFailure = $false
        }
    },
    [pscustomobject]@{
        Name = "DuplicateReplay"
        ExpectedFinalResult = "Successful"
        ExpectedEvidence = "Same input and idempotency keys are replayed without creating duplicate downstream records."
        Payload = [ordered]@{
            opportunityId = $duplicateOpportunityId
            accountId = $duplicateAccountId
            accountName = "Duplicate Replay Test"
            totalAmount = 20000
            currency = "USD"
            countryCode = "US"
            correlationId = $duplicateCorrelationId
            simulateZendeskFailure = $false
        }
    },
    [pscustomobject]@{
        Name = "MissingAccountId"
        ExpectedFinalResult = "Failed at validation"
        ExpectedEvidence = "Step 7 returns HTTP 400 because accountId is missing."
        Payload = [ordered]@{
            opportunityId = "OPP-NO-ACCOUNT-$runId"
            accountName = "Missing Account Test"
            totalAmount = 15000
            currency = "USD"
            countryCode = "US"
            correlationId = "missing-account-$runId"
            simulateZendeskFailure = $false
        }
    },
    [pscustomobject]@{
        Name = "MissingTotalAmount"
        ExpectedFinalResult = "Failed at validation"
        ExpectedEvidence = "Step 7 returns HTTP 400 because totalAmount is missing."
        Payload = [ordered]@{
            opportunityId = "OPP-NO-AMOUNT-$runId"
            accountId = "ACC-NO-AMOUNT-$runId"
            accountName = "Missing Amount Test"
            currency = "USD"
            countryCode = "US"
            correlationId = "missing-amount-$runId"
            simulateZendeskFailure = $false
        }
    },
    [pscustomobject]@{
        Name = "ZeroAmount"
        ExpectedFinalResult = "Failed at validation"
        ExpectedEvidence = "Step 7 returns HTTP 400 because totalAmount must be at least 0.01."
        Payload = [ordered]@{
            opportunityId = "OPP-ZERO-$runId"
            accountId = "ACC-ZERO-$runId"
            accountName = "Zero Amount Test"
            totalAmount = 0
            currency = "USD"
            countryCode = "US"
            correlationId = "zero-amount-$runId"
            simulateZendeskFailure = $false
        }
    },
    [pscustomobject]@{
        Name = "ConflictSeed"
        ExpectedFinalResult = "Successful"
        ExpectedEvidence = "Seeds the validator idempotency cache for the conflict test."
        Payload = [ordered]@{
            opportunityId = $conflictOpportunityId
            accountId = $conflictAccountId
            accountName = "Idempotency Conflict Test"
            totalAmount = 10000
            currency = "USD"
            countryCode = "US"
            correlationId = "conflict-seed-$runId"
            simulateZendeskFailure = $false
        }
    },
    [pscustomobject]@{
        Name = "ConflictChanged"
        ExpectedFinalResult = "Failed at validation"
        ExpectedEvidence = "Step 7 returns HTTP 409: same validation idempotency key but a changed request body."
        Payload = [ordered]@{
            opportunityId = $conflictOpportunityId
            accountId = $conflictAccountId
            accountName = "Idempotency Conflict Test"
            totalAmount = 11000
            currency = "USD"
            countryCode = "US"
            correlationId = "conflict-changed-$runId"
            simulateZendeskFailure = $false
        }
    }
)

$selectedTests = @($testCases | Where-Object { $_.Name -in $Cases })

function Get-MockState {
    param([string]$BaseUrl)

    try {
        return Invoke-RestMethod -Method Get -Uri "$($BaseUrl.TrimEnd('/'))/state"
    }
    catch {
        return [pscustomobject]@{ stateReadError = $_.Exception.Message }
    }
}

function ConvertTo-WorkatoWebhookPayload {
    param([System.Collections.IDictionary]$FlatPayload)

    $opportunity = [ordered]@{
        opportunity_id = $FlatPayload.opportunityId
        name = $FlatPayload.accountName
        stage = "Closed Won"
        close_date = (Get-Date -Format "yyyy-MM-dd")
        currency = $FlatPayload.currency
    }
    if ($FlatPayload.Contains("totalAmount")) {
        $opportunity.amount = $FlatPayload.totalAmount
    }

    $account = [ordered]@{
        name = $FlatPayload.accountName
        billing_country = $FlatPayload.countryCode
    }
    if ($FlatPayload.Contains("accountId")) {
        $account.account_id = $FlatPayload.accountId
    }

    return [ordered]@{
        event_id = "EVT-$($FlatPayload.opportunityId)"
        correlation_id = $FlatPayload.correlationId
        event_type = "OPPORTUNITY_CLOSED_WON"
        occurred_at = (Get-Date).ToUniversalTime().ToString("o")
        opportunity = $opportunity
        account = $account
        customer = [ordered]@{ admin_email = "integration-test@example.test" }
        source = "salesforce"
        simulate_zendesk_failure = $FlatPayload.simulateZendeskFailure
    }
}

if ($PreviewOnly) {
    Write-Host "PREVIEW ONLY - no webhooks will be sent and no Workato credits will be used." -ForegroundColor Yellow
}
elseif ($ResetMockState) {
    Write-Host "Resetting downstream mock state..." -ForegroundColor Cyan
    Invoke-RestMethod -Method Post -Uri "$($MockBaseUrl.TrimEnd('/'))/reset" | Out-Null
}

$stateBefore = $null
if (-not $PreviewOnly) {
    $stateBefore = Get-MockState -BaseUrl $MockBaseUrl
}

$results = @()

for ($index = 0; $index -lt $selectedTests.Count; $index++) {
    $testCase = $selectedTests[$index]
    $wirePayload = ConvertTo-WorkatoWebhookPayload -FlatPayload $testCase.Payload
    $payloadJson = $wirePayload | ConvertTo-Json -Depth 8 -Compress
    $accepted = $false
    $webhookResponse = $null
    $submissionError = $null

    Write-Host "[$($index + 1)/$($selectedTests.Count)] $($testCase.Name)" -ForegroundColor Cyan
    Write-Host "  Correlation ID: $($testCase.Payload.correlationId)"
    Write-Host "  Expected: $($testCase.ExpectedFinalResult)"

    if ($PreviewOnly) {
        Write-Host "  Payload: $payloadJson"
    }
    else {
        try {
            $webhookResponse = Invoke-RestMethod `
                -Method Post `
                -Uri $WebhookUrl `
                -ContentType "application/json" `
                -Body $payloadJson
            $accepted = $true
            Write-Host "  Webhook accepted by Workato." -ForegroundColor Green
        }
        catch {
            $submissionError = $_.Exception.Message
            Write-Host "  Webhook submission failed: $submissionError" -ForegroundColor Red
        }
    }

    $results += [pscustomobject]@{
        testName = $testCase.Name
        correlationId = $testCase.Payload.correlationId
        opportunityId = $testCase.Payload.opportunityId
        expectedFinalResult = $testCase.ExpectedFinalResult
        expectedEvidence = $testCase.ExpectedEvidence
        webhookAccepted = $accepted
        webhookResponse = $webhookResponse
        submissionError = $submissionError
        payload = $wirePayload
        observedWorkatoResult = "Fill in after checking the Workato job"
    }

    if (-not $PreviewOnly -and $DelaySeconds -gt 0 -and $index -lt ($selectedTests.Count - 1)) {
        Start-Sleep -Seconds $DelaySeconds
    }
}

$stateAfter = $null
if (-not $PreviewOnly) {
    $stateAfter = Get-MockState -BaseUrl $MockBaseUrl
}

# The runner lives three levels below the repository root after the automation
# package reorganization. Keep generated evidence in the shared ignored folder.
$resultDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\..\test-results"))
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null
$reportPath = Join-Path $resultDirectory "workato-suite-$runId.json"

$report = [ordered]@{
    runId = $runId
    generatedAt = (Get-Date).ToString("o")
    previewOnly = [bool]$PreviewOnly
    webhookHost = if ($WebhookUrl) { ([uri]$WebhookUrl).Host } else { $null }
    mockBaseUrl = $MockBaseUrl
    note = "Webhook acceptance only proves Workato received the event. Verify each final job result in Workato by correlationId."
    mockStateBefore = $stateBefore
    mockStateAfter = $stateAfter
    tests = $results
}

$report | ConvertTo-Json -Depth 15 | Set-Content -Path $reportPath -Encoding UTF8

Write-Host ""
Write-Host "Suite finished. Report: $reportPath" -ForegroundColor Green
$results | Select-Object testName, correlationId, expectedFinalResult, webhookAccepted | Format-Table -AutoSize

if (-not $PreviewOnly) {
    Write-Host "Open Workato Jobs and match each job using the correlationId in this report." -ForegroundColor Yellow
}
