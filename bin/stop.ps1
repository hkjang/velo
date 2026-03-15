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

$pid = $null

# Try PID file first
if (Test-Path $PidFile) {
    $pid = [int](Get-Content $PidFile -ErrorAction SilentlyContinue)
    if ($pid -and -not (Get-Process -Id $pid -ErrorAction SilentlyContinue)) {
        Write-Host "[INFO] PID $pid from file is not running."
        Remove-Item $PidFile -ErrorAction SilentlyContinue
        $pid = $null
    }
}

# Fallback: search by command line
if (-not $pid) {
    $proc = Get-WmiObject Win32_Process -Filter "CommandLine LIKE '%was-bootstrap%jar-with-dependencies%'" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($proc) { $pid = $proc.ProcessId }
}

if (-not $pid) {
    Write-Host "[INFO] No Velo WAS process found."
    return
}

Write-Host "  Found PID: $pid"

if ($Force) {
    Write-Host "  Force killing..."
    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "  Graceful shutdown (timeout: ${Timeout}s)..."
    Stop-Process -Id $pid -ErrorAction SilentlyContinue

    $elapsed = 0
    while ((Get-Process -Id $pid -ErrorAction SilentlyContinue) -and $elapsed -lt $Timeout) {
        Start-Sleep -Seconds 1
        $elapsed++
        if ($elapsed % 5 -eq 0) {
            Write-Host "  Waiting... (${elapsed}s / ${Timeout}s)"
        }
    }

    if (Get-Process -Id $pid -ErrorAction SilentlyContinue) {
        Write-Host "  Timeout. Force killing..."
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    }
}

Remove-Item $PidFile -ErrorAction SilentlyContinue
Write-Host "Velo WAS stopped." -ForegroundColor Green
