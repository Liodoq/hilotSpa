<#
    deploy-frontend.ps1 - build the Angular bundle and ship it to EC2.

    Put this in the repo root (beside hilotspa-frontend/) and run it there.

    A frontend deploy is a FILE COPY. Caddy serves the bundle straight off
    disk, so there is nothing to rebuild, nothing to restart, no downtime.

    Usage:
        .\deploy-frontend.ps1
        .\deploy-frontend.ps1 -Server ubuntu@47.129.178.114

    ASCII only, deliberately. PowerShell 5.1 reads .ps1 as ANSI unless the
    file carries a BOM, so a stray em-dash in a comment becomes mojibake and
    takes the parser down with it.
#>
param(
    [string]$Server = "ubuntu@47.129.178.114",
    [string]$Key    = "$HOME\hilotinspa.pem",
    [string]$Remote = "/home/ubuntu/hilotSpa/site"
)

$ErrorActionPreference = 'Stop'

# Guard the remote path before anything is deleted. This script clears
# $Remote; a typo or an empty value would run that delete against the wrong
# directory on a live server, and there is no undo for that over SSH.
if ($Remote -notmatch '^/home/[a-z0-9_-]+/.+/site$') {
    throw "Refusing to deploy: -Remote '$Remote' does not look like the site folder."
}
if (-not (Test-Path $Key)) {
    throw "No key at $Key. Pass -Key with the right path."
}

# ---------------------------------------------------------------- build
Push-Location (Join-Path $PSScriptRoot 'hilotspa-frontend')
try {
    Write-Host "Building the production bundle..." -ForegroundColor Cyan
    npm run build
    if ($LASTEXITCODE -ne 0) {
        # Fail HERE, with nothing uploaded. A half-shipped bundle is worse
        # than a stale one: the old index.html goes on asking for chunk
        # files the new build renamed, so the site breaks only on refresh.
        throw "ng build failed. Nothing was uploaded; the live site is untouched."
    }

    # Find the folder that actually holds index.html rather than assuming
    # dist/<project>/browser. Angular has moved this output path twice, and
    # a wrong guess uploads an empty folder with no error at all.
    $index = Get-ChildItem -Path dist -Recurse -Filter index.html -ErrorAction SilentlyContinue |
             Sort-Object { $_.FullName.Length } | Select-Object -First 1
    if (-not $index) { throw "No index.html under dist. Check outputPath in angular.json." }
    $src = $index.Directory.FullName
    Write-Host "Built: $src" -ForegroundColor DarkGray
}
finally { Pop-Location }

# ---------------------------------------------------------------- upload
# Two hops rather than one: PowerShell does not expand "dist\...\*" for
# native commands, so scp would receive a literal asterisk and fail.
$stage = "/home/ubuntu/dist-new"

Write-Host "Uploading..." -ForegroundColor Cyan
ssh -i $Key $Server "rm -rf $stage"
scp -i $Key -r $src "${Server}:$stage"
if ($LASTEXITCODE -ne 0) { throw "Upload failed. The live site is untouched." }

# Replace the CONTENTS, never the directory. $Remote is bind-mounted into
# the Caddy container and a bind mount follows the inode: move the directory
# aside and Caddy keeps serving the old one, silently, until the container is
# recreated. That reads as "my deploy did nothing" and wastes an hour.
Write-Host "Swapping in..." -ForegroundColor Cyan
ssh -i $Key $Server "find '$Remote' -mindepth 1 -delete && cp -r $stage/. '$Remote/' && rm -rf $stage"
if ($LASTEXITCODE -ne 0) { throw "Swap failed. Check the server: the site may be empty." }

# Verify. Do not report success just because scp exited 0.
$check = ssh -i $Key $Server "test -f '$Remote/index.html' && echo OK"
if ($check -ne 'OK') { throw "index.html is not on the server. The site is broken; rerun." }

Write-Host ""
Write-Host "Deployed. Hard-refresh the site with Ctrl+Shift+R." -ForegroundColor Green
