<#
.SYNOPSIS
    Downsizes geneav-vm to a cheaper burstable size, once Azure has capacity for one.

.DESCRIPTION
    PARKED as of 26 Jul 2026 — do not schedule this yet. See "WHY THIS IS PARKED"
    at the end of this comment block.

    The VM runs on a Standard_D2s_v3 (8 GiB, ~$70/month) to serve a stack that
    measures ~1.4 GiB and idles at a load average of 0.00. The intended target is
    Standard_B1ms (~$15/month), and the subscription HAS quota for it — 10 BS-family
    vCPUs, none used. What it does not have is capacity: as of 26 Jul 2026 both
    B1ms and B2s fail in eastus with

        SkuNotAvailable ... Capacity Restrictions

    both live and while deallocated. That is a transient, region-side condition, so
    this script re-checks daily and takes the resize the moment one is offered.

    IMPORTANT — this deliberately causes downtime. A family change requires the VM
    to be deallocated, so the site is offline for roughly 5-10 minutes. It only
    reaches that point after the target SKU shows up as offered, which is the
    signal that the resize will actually succeed. On any failure it restores the
    original size and starts the VM again, so the machine is never left stopped.

    Delete this script and its scheduled task once the resize has landed.

    WHY THIS IS PARKED (26 Jul 2026)
    --------------------------------
    Two things must be true before this is scheduled, and neither is today:

    1. Umami. Marketing plan Phase 0 adds a self-hosted analytics container
       (docker-compose.prod.yml) plus its own database in the existing Postgres.
       That is roughly another 250-500 MB on top of the ~1.4 GiB measured below.
       B1ms is 2 GiB total, and ClamAV alone holds ~1.5-2 GB resident. It does
       not fit. B2s (4 GiB) plausibly does, but has not been measured with Umami
       running.

    2. The ConcurrentDatabaseReload prerequisite in .NOTES is NOT met. That note
       claims the fix is deployed; it is not. There is no clamd.conf, no
       ConcurrentDatabaseReload setting, and no ClamAV config mount anywhere in
       the repo, and docs/business-case.md §4.1 still records it as unset. The
       only guard against the freshclam reload memory spike is the 3 GB container
       limit — which a 2 GiB VM cannot honour.

    To resume: set ConcurrentDatabaseReload for real, benchmark the stack with
    Umami running, then target B2s rather than B1ms unless the measurement says
    otherwise.

.NOTES
    Prerequisite: the ClamAV ConcurrentDatabaseReload fix must already be
    deployed. Without it a signature-database reload transiently doubles
    ClamAV's memory and would OOM a 2 GiB box. As of 26 Jul 2026 this is
    UNMET — see "WHY THIS IS PARKED" above.
#>

[CmdletBinding()]
param(
    [string]  $ResourceGroup = 'GENEAV-RG',
    [string]  $VmName        = 'geneav-vm',
    [string]  $Location      = 'eastus',
    # Cheapest first. B1ms is the target; B2s is the fallback worth taking if it
    # appears first, since it is still less than half the current bill.
    [string[]]$Targets       = @('Standard_B1ms', 'Standard_B2s'),
    [string]  $SiteUrl       = 'https://geneav.com',
    [string]  $LogPath       = "$HOME\.geneav\vm-downsize.log",
    # Report what would happen, change nothing.
    [switch]  $WhatIfOnly,
    # Required to actually resize while this script is parked. Without it the
    # script refuses, because an accidental run takes geneav.com offline for
    # 5-10 minutes and may OOM the box on the way back up.
    [switch]  $Resume
)

$ErrorActionPreference = 'Stop'

if (-not $Resume -and -not $WhatIfOnly) {
    Write-Host "PARKED: this script is deferred pending the ClamAV ConcurrentDatabaseReload fix"
    Write-Host "and a benchmark of the stack with the Umami analytics container running."
    Write-Host "See the WHY THIS IS PARKED block at the top of this file."
    Write-Host "Re-run with -WhatIfOnly to probe capacity, or -Resume to override."
    exit 0
}
New-Item -ItemType Directory -Force -Path (Split-Path $LogPath) | Out-Null

function Write-Log {
    param([string]$Message)
    $line = "{0}  {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -Path $LogPath -Value $line -Encoding utf8
    Write-Host $line
}

function Test-Site {
    try {
        $r = Invoke-WebRequest -Uri $SiteUrl -TimeoutSec 25 -UseBasicParsing
        return $r.StatusCode -eq 200
    } catch { return $false }
}

try {
    $current = az vm show -g $ResourceGroup -n $VmName --query hardwareProfile.vmSize -o tsv 2>$null
    if (-not $current) {
        # Almost always an expired az login rather than a missing VM. Say so plainly:
        # a silent daily no-op is worse than a loud one.
        Write-Log "ABORT: could not read VM size. Run 'az login' — the cached token has likely expired."
        exit 1
    }
    if ($current -in $Targets) {
        Write-Log "Already on $current. Nothing to do — this script and its scheduled task can be removed."
        exit 0
    }

    # Read-only capacity probe. A SKU absent from list-skus is one Azure will refuse
    # to place, so checking here avoids deallocating into a resize that cannot work.
    $offered = az vm list-skus -l $Location --resource-type virtualMachines --query "[].name" -o tsv 2>$null
    $available = $Targets | Where-Object { $offered -contains $_ }

    if (-not $available) {
        Write-Log "No target SKU offered in $Location yet (checked: $($Targets -join ', ')). Still on $current."
        exit 0
    }

    $target = $available[0]
    Write-Log "$target is now offered in $Location. Current size $current."

    if ($WhatIfOnly) {
        Write-Log "WhatIfOnly: would deallocate, resize to $target, and start. No action taken."
        exit 0
    }

    Write-Log "Deallocating (site goes down here)..."
    az vm deallocate -g $ResourceGroup -n $VmName --only-show-errors | Out-Null

    $resized = $false
    try {
        az vm resize -g $ResourceGroup -n $VmName --size $target --only-show-errors | Out-Null
        $resized = ($LASTEXITCODE -eq 0)
    } catch { $resized = $false }

    if (-not $resized) {
        # Capacity can vanish between the probe and the request. Put it back exactly
        # as it was; being on the expensive size is much better than being stopped.
        Write-Log "Resize to $target FAILED despite being listed. Restoring $current."
        az vm resize -g $ResourceGroup -n $VmName --size $current --only-show-errors | Out-Null
    }

    Write-Log "Starting VM..."
    az vm start -g $ResourceGroup -n $VmName --only-show-errors | Out-Null

    $final = az vm show -g $ResourceGroup -n $VmName --query hardwareProfile.vmSize -o tsv 2>$null
    Write-Log "VM running on $final."

    # Containers restart on their own (restart: unless-stopped), but ClamAV has to
    # load its database before the backend passes a health check, so allow minutes.
    $up = $false
    foreach ($i in 1..30) {
        if (Test-Site) { $up = $true; break }
        Start-Sleep -Seconds 20
    }
    Write-Log $(if ($up) { "Site responding. Downsize to $final complete." }
                else      { "WARNING: site not responding yet after ~10 min. Check the VM." })
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    # Never leave the VM stopped because this script threw.
    try {
        $p = az vm get-instance-view -g $ResourceGroup -n $VmName `
             --query "instanceView.statuses[?starts_with(code,'PowerState')].displayStatus" -o tsv 2>$null
        if ($p -and $p -ne 'VM running') {
            Write-Log "VM is '$p' after the error — starting it."
            az vm start -g $ResourceGroup -n $VmName --only-show-errors | Out-Null
        }
    } catch { Write-Log "Could not confirm VM power state. CHECK MANUALLY." }
    exit 1
}
