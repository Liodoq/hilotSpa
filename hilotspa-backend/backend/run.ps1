# Loads .env into the current PowerShell session, then starts the backend.
# Usage:  .\run.ps1
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
& "$PSScriptRoot\mvnw.cmd" spring-boot:run
