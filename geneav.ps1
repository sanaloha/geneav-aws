<#
.SYNOPSIS
    Start/stop the local geneav stack (clamav + backend + frontend).
.EXAMPLE
    .\geneav.ps1 start
    .\geneav.ps1 logs backend
    .\geneav.ps1 scan .\sample.pdf
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'status', 'logs', 'reset', 'scan', 'help')]
    [string]$Command = 'help',

    [Parameter(Position = 1, ValueFromRemainingArguments = $true)]
    [string[]]$Rest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$ApiBase = 'http://localhost:8080'
$FrontendUrl = 'http://localhost:3000'
# ClamAV downloads its signature DB on first boot, and the backend only starts
# once clamd reports healthy, so a cold start is much slower than a warm one.
$HealthTimeout = 240
if ($env:GENEAV_HEALTH_TIMEOUT) { $HealthTimeout = [int]$env:GENEAV_HEALTH_TIMEOUT }

function Show-Usage {
    @"
Usage: .\geneav.ps1 <command>

Commands:
  start        Build if needed, start the stack, wait until the API is healthy
  stop         Stop and remove containers (keeps the ClamAV signature DB)
  restart      stop, then start
  status       Show container status and backend health
  logs [svc]   Follow logs, all services or one of: clamav backend frontend
  reset        Stop and delete the ClamAV signature DB volume (forces re-download)
  scan <file>  Scan a file through the running API (needs GENEAV_API_KEY)
  help         Show this message

Env:
  GENEAV_HEALTH_TIMEOUT  Seconds to wait for health on start (default 240)
  GENEAV_API_KEY         gav_live_... key used by 'scan'. The API has no
                         anonymous tier; mint one by POSTing your email to
                         $ApiBase/api/v1/signup
"@
}

function Assert-Docker {
    docker version --format '{{.Server.Version}}' | Out-Null
    if (-not $?) { throw 'The Docker daemon is not reachable. Is Docker Desktop running?' }
}

function Get-HealthJson {
    try {
        $r = Invoke-WebRequest -Uri "$ApiBase/api/v1/health" -UseBasicParsing -TimeoutSec 5
        return $r.Content
    } catch {
        return $null
    }
}

function Wait-ForHealth {
    $deadline = (Get-Date).AddSeconds($HealthTimeout)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne (Get-HealthJson)) { return $true }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 2
    }
    return $false
}

function Invoke-Start {
    Assert-Docker
    docker compose up --build -d
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed (exit $LASTEXITCODE)" }

    Write-Host 'waiting for the API to come up ' -NoNewline
    if (Wait-ForHealth) {
        Write-Host ' up'
        Write-Host ''
        docker compose ps --format '{{.Name}}\t{{.Status}}'
        Write-Host ''
        Write-Host "health:   $(Get-HealthJson)"
        Write-Host ''
        Write-Host "Frontend: $FrontendUrl"
        Write-Host "API:      $ApiBase/api/v1/scan"
        Write-Host "Docs:     $ApiBase/docs"
    } else {
        Write-Host " timed out after $HealthTimeout s"
        Write-Host ''
        Write-Host 'Recent backend logs:'
        docker compose logs --tail 40 backend
        exit 1
    }
}

function Invoke-Stop {
    Assert-Docker
    docker compose down
}

function Invoke-Status {
    Assert-Docker
    docker compose ps --format '{{.Name}}\t{{.Status}}\t{{.Ports}}'
    Write-Host ''
    $h = Get-HealthJson
    if ($null -ne $h) {
        Write-Host "health: $h"
    } else {
        Write-Host "health: unreachable at $ApiBase/api/v1/health"
    }
}

function Invoke-Logs {
    Assert-Docker
    if ($Rest -and $Rest.Count -gt 0) {
        docker compose logs -f --tail 100 @Rest
    } else {
        docker compose logs -f --tail 100
    }
}

function Invoke-Reset {
    Assert-Docker
    # -v drops the clamav-db volume; the next start re-downloads the signature DB.
    docker compose down -v
}

function Invoke-Scan {
    if (-not $Rest -or $Rest.Count -lt 1) { throw 'usage: .\geneav.ps1 scan <file>' }
    $file = $Rest[0]
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "no such file: $file" }
    $full = (Resolve-Path -LiteralPath $file).Path
    if (-not $env:GENEAV_API_KEY) {
        throw "set GENEAV_API_KEY first - /api/v1/scan requires an API key. Mint one by POSTing {""email"":""you@example.com""} to $ApiBase/api/v1/signup and use the returned apiKey."
    }
    # curl.exe (not the PS alias) so multipart upload matches the documented API call.
    curl.exe -fsS -H "Authorization: Bearer $env:GENEAV_API_KEY" -F "file=@$full" "$ApiBase/api/v1/scan"
    Write-Host ''
}

switch ($Command) {
    'start'   { Invoke-Start }
    'stop'    { Invoke-Stop }
    'restart' { Invoke-Stop; Invoke-Start }
    'status'  { Invoke-Status }
    'logs'    { Invoke-Logs }
    'reset'   { Invoke-Reset }
    'scan'    { Invoke-Scan }
    default   { Show-Usage }
}
