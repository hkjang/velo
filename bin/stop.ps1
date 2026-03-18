# ─────────────────────────────────────────────────────────
#  Velo WAS - Stop Server (PowerShell)
# ─────────────────────────────────────────────────────────
param(
    [switch]$Force,
    [int]$Timeout = 30,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$PidFile = Join-Path $ProjectRoot "velo-was.pid"

if ($Help) {
    Write-Host @"
Usage: .\bin\stop.ps1 [-Force] [-Timeout <sec>]

Options:
  -Force          Force kill immediately
  -Timeout <sec>  Graceful shutdown timeout (default: 30)
  -Help           Show this help
"@
    return
}

Write-Host "════════════════════════════════════════════════════════"
Write-Host "  Velo WAS - Stopping"
Write-Host "════════════════════════════════════════════════════════"

$serverPid = $null

# Try PID file first
if (Test-Path $PidFile) {
    $serverPid = [int](Get-Content $PidFile -ErrorAction SilentlyContinue)
    if ($serverPid -and -not (Get-Process -Id $serverPid -ErrorAction SilentlyContinue)) {
        Write-Host "[INFO] PID $serverPid from file is not running."
        Remove-Item $PidFile -ErrorAction SilentlyContinue
        $serverPid = $null
    }
}

# Fallback: search by command line
if (-not $serverPid) {
    $proc = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*was-bootstrap*jar-with-dependencies.jar*' } |
        Select-Object -First 1
    if ($proc) { $serverPid = $proc.ProcessId }
}

if (-not $serverPid) {
    Write-Host "[INFO] No Velo WAS process found."
    return
}

Write-Host "  Found PID: $serverPid"

if ($Force) {
    Write-Host "  Force killing..."
    Stop-Process -Id $serverPid -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "  Graceful shutdown (timeout: ${Timeout}s)..."
    Stop-Process -Id $serverPid -ErrorAction SilentlyContinue

    $elapsed = 0
    while ((Get-Process -Id $serverPid -ErrorAction SilentlyContinue) -and $elapsed -lt $Timeout) {
        Start-Sleep -Seconds 1
        $elapsed++
        if ($elapsed % 5 -eq 0) {
            Write-Host "  Waiting... (${elapsed}s / ${Timeout}s)"
        }
    }

    if (Get-Process -Id $serverPid -ErrorAction SilentlyContinue) {
        Write-Host "  Timeout. Force killing..."
        Stop-Process -Id $serverPid -Force -ErrorAction SilentlyContinue
    }
}

Remove-Item $PidFile -ErrorAction SilentlyContinue
Write-Host "Velo WAS stopped." -ForegroundColor Green
