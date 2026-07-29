param(
    [ValidateSet('success', 'transient-failure', 'both')]
    [string]$Scenario = 'both'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$tempDirectory = Join-Path $repoRoot 'tmp\demo'
New-Item -ItemType Directory -Force -Path $tempDirectory | Out-Null

$serviceProcess = $null
$mockProcess = $null

function Wait-ForEndpoint([string]$Uri, [string]$Name) {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            Invoke-RestMethod -Uri $Uri -TimeoutSec 1 | Out-Null
            return
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "$Name did not become ready. Inspect logs under $tempDirectory."
}

try {
    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot 'mvnw.cmd') --batch-mode --no-transfer-progress -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Could not package the validation service.' }
    } finally {
        Pop-Location
    }

    $javaExecutable = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        (Get-Command 'java' -ErrorAction Stop).Source
    }

    $serviceProcess = Start-Process `
        -FilePath $javaExecutable `
        -ArgumentList '-jar', (Join-Path $repoRoot 'target\order-validation-service-0.0.1-SNAPSHOT.jar') `
        -WorkingDirectory $repoRoot `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $tempDirectory 'validation-service.log') `
        -RedirectStandardError (Join-Path $tempDirectory 'validation-service-error.log') `
        -PassThru

    $mockProcess = Start-Process `
        -FilePath 'node' `
        -ArgumentList (Join-Path $repoRoot 'demo\mock-systems.mjs') `
        -WorkingDirectory $repoRoot `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $tempDirectory 'mock-systems.log') `
        -RedirectStandardError (Join-Path $tempDirectory 'mock-systems-error.log') `
        -PassThru

    Wait-ForEndpoint 'http://127.0.0.1:8080/actuator/health' 'Validation service'
    Wait-ForEndpoint 'http://127.0.0.1:8081/health' 'Mock systems'

    $scenarios = if ($Scenario -eq 'both') { @('success', 'transient-failure') } else { @($Scenario) }
    foreach ($selectedScenario in $scenarios) {
        Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/reset' | Out-Null
        Write-Host "`n=== $selectedScenario ==="
        & node (Join-Path $repoRoot 'demo\run-workflow.mjs') "--scenario=$selectedScenario"
        if ($LASTEXITCODE -ne 0) { throw "Demo scenario failed: $selectedScenario" }
    }
} finally {
    if ($mockProcess -and -not $mockProcess.HasExited) { Stop-Process -Id $mockProcess.Id }
    if ($serviceProcess -and -not $serviceProcess.HasExited) { Stop-Process -Id $serviceProcess.Id }
}
