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
    Updated 26 Jul 2026 with real measurements. Two of the three blockers are gone.

    1. RESOLVED - memory. Everything here previously rested on "ClamAV holds
       ~1.5-2 GB resident", which was an estimate and was roughly 2x high.
       Measured on this VM: clamd VmRSS 974 MB, VmHWM 987 MB, and the WHOLE stack
       including Umami is 2.0 GB of 7.8 GB.

    2. RESOLVED - reload spike. CLAMD_CONF_ConcurrentDatabaseReload=no is now set
       in docker-compose.yml, so a signature reload no longer loads the new
       database alongside the old one. (The earlier .NOTES claim that this was
       already deployed was simply wrong; it is deployed now.)

    3. OPEN - capacity. Throughput has still never been benchmarked
       (docs/business-case.md 5.6), so nobody knows how many concurrent scans a
       given SKU sustains. Halving the vCPU count without that number is guessing.

    Target change: B1ms (2 GiB) is OFF the list. The stack already uses 2.0 GB, so
    it would not fit even now that clamd is smaller than believed. B2s (4 GiB,
    2 vCPU) fits comfortably on memory and is the only sensible target.

    To resume: land the throughput benchmark, confirm 2 vCPU sustains the expected
    concurrency, then run with -Resume.

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
    # B1ms (2 GiB) was removed 26 Jul 2026: the stack measures 2.0 GB, so it does
    # not fit regardless of clamd being smaller than previously believed.
    [string[]]$Targets       = @('Standard_B2s'),
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
