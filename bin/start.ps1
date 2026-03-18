# ─────────────────────────────────────────────────────────
#  Velo WAS - Start Server (PowerShell)
# ─────────────────────────────────────────────────────────
param(
    [string]$Config,
    [switch]$Daemon,
    [string]$JvmOpts,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

function Get-BootstrapFatJar {
    $targetDir = Join-Path $ProjectRoot "was-bootstrap\target"
    Get-ChildItem -Path $targetDir -Filter "was-bootstrap-*-jar-with-dependencies.jar" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime, Name -Descending |
        Select-Object -First 1
}

if ($Help) {
    Write-Host @"
Usage: .\bin\start.ps1 [-Config <path>] [-Daemon] [-JvmOpts <opts>]

Options:
  -Config <path>    Config file (default: conf\server.yaml)
  -Daemon           Run in background
  -JvmOpts <opts>   JVM options (default: -Xms256m -Xmx1g -XX:+UseZGC)
  -Help             Show this help

Examples:
  .\bin\start.ps1                           # Foreground
  .\bin\start.ps1 -Daemon                   # Background
  .\bin\start.ps1 -Config conf\prod.yaml    # Custom config
"@
    return
}

# ── Toolchain ──
$LocalJdk = Join-Path $ProjectRoot ".tools\jdk\jdk-21.0.10+7"
if (Test-Path $LocalJdk) {
    $env:JAVA_HOME = $LocalJdk
} elseif (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set"
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$Java = Join-Path $env:JAVA_HOME "bin\java.exe"

# ── Config ──
$fatJarItem = Get-BootstrapFatJar
if (-not $Config)  { $Config = Join-Path $ProjectRoot "conf\server.yaml" }
if (-not $JvmOpts) { $JvmOpts = if ($env:VELO_JVM_OPTS) { $env:VELO_JVM_OPTS } else { "-Xms256m -Xmx1g -XX:+UseZGC" } }
$LogDir = Join-Path $ProjectRoot "logs"
$PidFile = Join-Path $ProjectRoot "velo-was.pid"

# ── Pre-check ──
if (-not $fatJarItem) {
    Write-Host "[WARN] Fat jar not found. Building..." -ForegroundColor Yellow
    & "$ScriptDir\build.ps1" -Package -Quiet
    $fatJarItem = Get-BootstrapFatJar
}

if (-not $fatJarItem) {
    throw "Fat jar not found under was-bootstrap\\target"
}
$FatJar = $fatJarItem.FullName

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

# Check already running
if (Test-Path $PidFile) {
    $existingPid = Get-Content $PidFile -ErrorAction SilentlyContinue
    if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
        throw "Velo WAS is already running (PID: $existingPid). Use .\bin\stop.ps1 first."
    }
    Remove-Item $PidFile -ErrorAction SilentlyContinue
}

Write-Host "════════════════════════════════════════════════════════"
Write-Host "  Velo WAS - Starting"
Write-Host "  JAVA_HOME : $env:JAVA_HOME"
Write-Host "  Fat Jar   : $FatJar"
Write-Host "  Config    : $Config"
Write-Host "  JVM Opts  : $JvmOpts"
Write-Host "  Mode      : $(if ($Daemon) {'Daemon'} else {'Foreground'})"
Write-Host "════════════════════════════════════════════════════════"

$jvmArgList = $JvmOpts -split '\s+'
$allArgs = $jvmArgList + @(
    "-Dvelo.config=$Config",
    "-Dvelo.home=$ProjectRoot",
    "-jar", $FatJar
)

if ($Daemon) {
    $logFile = Join-Path $LogDir "velo-was.out"
    $proc = Start-Process -FilePath $Java -ArgumentList $allArgs `
        -RedirectStandardOutput $logFile -RedirectStandardError (Join-Path $LogDir "velo-was.err") `
        -WindowStyle Hidden -PassThru
    $proc.Id | Out-File -FilePath $PidFile -Encoding ASCII
    Write-Host "Velo WAS started (PID: $($proc.Id))" -ForegroundColor Green
    Write-Host "  Log: $logFile"
    Write-Host "  PID: $PidFile"

    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        Write-Host "[ERROR] Server failed to start. Check logs." -ForegroundColor Red
        Get-Content $logFile -Tail 20
        Remove-Item $PidFile -ErrorAction SilentlyContinue
        exit 1
    }
} else {
    & $Java @allArgs
}
