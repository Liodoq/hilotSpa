# Loads .env into the current PowerShell session, then runs the test suite.
# Tests hit the real Postgres and the real security filter chain, so they need
# the same environment the app does.
# Usage:  .\test.ps1              (all tests)
#         .\test.ps1 FormsAccessControlTest   (one class)
param([string]$Test = "")

$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Error "No .env found. Copy .env.example to .env and fill it in."
    exit 1
}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $name, $value = $line.Split("=", 2)
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
    }
}
Write-Host "Loaded .env  (NODE_ID=$env:NODE_ID)" -ForegroundColor Green

if ($Test) {
    & "$PSScriptRoot\mvnw.cmd" test "-Dtest=$Test"
} else {
    & "$PSScriptRoot\mvnw.cmd" test
}
