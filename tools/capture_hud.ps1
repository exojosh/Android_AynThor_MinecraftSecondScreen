<#
.SYNOPSIS
    Drives the companion app through HUD scenarios and screenshots the result.

.DESCRIPTION
    Runs hud_sim.py in place of the real mod, restarts the app so it reconnects,
    and pulls a screenshot of the second display for each scenario. Requires
    `adb reverse tcp:48291 tcp:48291` to be active so the app's connection to
    127.0.0.1:48291 lands on this machine.

    Minecraft must NOT be running -- the real mod binds the same port on device.

.EXAMPLE
    adb reverse tcp:48291 tcp:48291
    .\capture_hud.ps1 -Jar .\mc_client.jar -Scenarios absorption,drowning -OutDir .\shots
#>
param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [Parameter(Mandatory = $true)][string[]]$Scenarios,
    [string]$OutDir = ".",
    [string]$Adb = "adb",
    # SurfaceFlinger display id of the Thor's bottom screen. Find yours with
    # `adb shell dumpsys SurfaceFlinger --display-id`.
    [string]$DisplayId = "4630946482288158084",
    [string]$Package = "com.exojosh.minecraftsecondscreen"
)

$ErrorActionPreference = "Stop"
$simPath = Join-Path $PSScriptRoot "hud_sim.py"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

foreach ($scenario in $Scenarios) {
    Get-Process python -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Milliseconds 800

    # -u so the log isn't buffered away when stdout isn't a TTY.
    Start-Process -FilePath "python" `
        -ArgumentList @("-u", $simPath, $Jar, $scenario) `
        -RedirectStandardOutput (Join-Path $OutDir "sim_$scenario.log") `
        -RedirectStandardError (Join-Path $OutDir "sim_$scenario.err") `
        -PassThru -WindowStyle Hidden | Out-Null
    Start-Sleep -Seconds 4

    & $Adb shell am force-stop $Package | Out-Null
    Start-Sleep -Milliseconds 800
    & $Adb shell am start -n "$Package/.MainActivity" | Out-Null
    Start-Sleep -Seconds 7

    & $Adb shell "screencap -p -d $DisplayId /sdcard/hud_capture.png" | Out-Null
    & $Adb pull /sdcard/hud_capture.png (Join-Path $OutDir "hud_$scenario.png") | Out-Null
    & $Adb shell rm /sdcard/hud_capture.png | Out-Null
    Write-Host "captured $scenario"
}

Get-Process python -ErrorAction SilentlyContinue | Stop-Process -Force
