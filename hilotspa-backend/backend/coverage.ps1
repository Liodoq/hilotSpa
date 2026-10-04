# Loads .env into the current PowerShell session, then runs the full suite
# through `verify` so JaCoCo writes its report.
#
# `test.ps1` stops at `test`, which runs the tests but produces no coverage
# report - the JaCoCo report execution is bound to `verify` on purpose, so an
# ordinary test run stays as fast as it was.
#
# The tests hit a REAL Postgres and the real security filter chain, so the
# database container has to be up first:
#     docker compose up -d db
#
# Usage:  .\coverage.ps1
#
# ASCII only, deliberately. PowerShell 5.1 reads .ps1 as ANSI unless the file
# carries a BOM, so a stray em-dash becomes mojibake and takes the parser down.

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

& "$PSScriptRoot\mvnw.cmd" verify
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Build failed. No coverage report was written." -ForegroundColor Red
    Write-Host "If this is 'Could not resolve placeholder', the .env is incomplete." -ForegroundColor DarkGray
    Write-Host "If it is a connection refused, the database container is not up:" -ForegroundColor DarkGray
    Write-Host "    docker compose up -d db" -ForegroundColor DarkGray
    exit $LASTEXITCODE
}

$report = Join-Path $PSScriptRoot "target\site\jacoco\index.html"
if (Test-Path $report) {
    Write-Host ""
    Write-Host "Coverage report: $report" -ForegroundColor Green
    Start-Process $report
} else {
    Write-Host ""
    Write-Host "Tests passed but no JaCoCo report was found at target\site\jacoco." -ForegroundColor Yellow
    Write-Host "Check that the jacoco-maven-plugin block is still in pom.xml." -ForegroundColor DarkGray
}
