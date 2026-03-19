# ─────────────────────────────────────────────────────────
#  Velo WAS - Build Script (PowerShell / Windows)
# ─────────────────────────────────────────────────────────
param(
    [switch]$Clean,
    [switch]$Test,
    [switch]$SkipTests,
    [switch]$Package,
    [switch]$Quiet,
    [switch]$Help,
    [Parameter(ValueFromRemainingArguments)]
    [string[]]$Modules
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
Usage: .\bin\build.ps1 [-Clean] [-Test] [-Package] [-Quiet] [modules...]

Options:
  -Clean       Clean before build
  -Test        Run tests
  -SkipTests   Skip tests (default)
  -Package     Create fat jar (mvn package)
  -Quiet       Quiet output
  -Help        Show this help

Modules:
  all          Build all modules (default)
  <name>       e.g. was-admin, was-webadmin, bootstrap

Examples:
  .\bin\build.ps1                       # Build all
  .\bin\build.ps1 was-admin             # Build was-admin
  .\bin\build.ps1 -Clean -Test          # Clean build + tests
  .\bin\build.ps1 -Package              # Create fat jar
"@
    return
}

# ── Toolchain ──
$LocalJdkCandidates = @(
    (Join-Path $ProjectRoot ".tools\jdk\jdk-21.0.10+7"),
    (Join-Path $ProjectRoot "tools\java\jdk-21.0.10+7")
)
$LocalMvnCandidates = @(
    (Join-Path $ProjectRoot ".tools\maven\apache-maven-3.9.13"),
    (Join-Path $ProjectRoot "tools\maven\apache-maven-3.9.13")
)

$LocalJdk = $LocalJdkCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
$LocalMvn = $LocalMvnCandidates | Where-Object { Test-Path (Join-Path $_ "bin\mvn.cmd") } | Select-Object -First 1

if ($LocalJdk) {
    $env:JAVA_HOME = $LocalJdk
} elseif (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set and local JDK not found under tools\\java or .tools\\jdk"
}

if ($LocalMvn) {
    $Mvn = Join-Path $LocalMvn "bin\mvn.cmd"
} else {
    $Mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if (-not $Mvn) { throw "Maven not found" }
}

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# ── Build args ──
$Goal = if ($Package) { "package" } else { "compile" }
$CleanGoal = if ($Clean) { "clean" } else { "" }
$SkipArg = if ($Test) { "" } else { "-DskipTests" }
$QuietArg = if ($Quiet) { "-q" } else { "" }

$PlOption = ""
$ModuleDisplay = "all"
if ($Modules -and $Modules.Count -gt 0 -and $Modules[0] -ne "all") {
    $normalized = $Modules | ForEach-Object {
        if ($_ -notlike "was-*") { "was-$_" } else { $_ }
    }
    $PlOption = "-pl $($normalized -join ',') -am"
    $ModuleDisplay = $normalized -join ", "
}

Write-Host "════════════════════════════════════════════════════════"
Write-Host "  Velo WAS Build"
Write-Host "  JAVA_HOME : $env:JAVA_HOME"
Write-Host "  Goal      : $CleanGoal $Goal"
Write-Host "  Modules   : $ModuleDisplay"
Write-Host "  Tests     : $(if ($SkipArg) {'skipped'} else {'enabled'})"
Write-Host "════════════════════════════════════════════════════════"

Push-Location $ProjectRoot
try {
    $args = @($CleanGoal, $Goal, $SkipArg, $QuietArg, $PlOption) | Where-Object { $_ }
    & $Mvn @args
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Build completed successfully." -ForegroundColor Green

if ($Package) {
    $fatJar = Get-BootstrapFatJar
    if ($fatJar) {
        $size = $fatJar.Length / 1MB
        Write-Host "  Artifact: $($fatJar.FullName)"
        Write-Host ("  Size    : {0:N1} MB" -f $size)
    }
}
