param(
    [string]$Version = "11.0.18",
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$majorVersion = ($Version -split '\.')[0]
$archiveUrl = "https://dlcdn.apache.org/tomcat/tomcat-$majorVersion/v$Version/src/apache-tomcat-$Version-src.tar.gz"
$componentRoot = Join-Path $RepoRoot "vendor/upstream/apache-tomcat/$Version"
$patchRoot = Join-Path $RepoRoot "vendor/patches/apache-tomcat/$Version"
$adapterRoot = Join-Path $RepoRoot "vendor/adapters/apache-tomcat/$Version"
$manifestPath = Join-Path $RepoRoot "third_party/reuse-manifest.json"

Write-Output "Preparing Apache Tomcat source intake"
Write-Output "Version: $Version"
Write-Output "Archive: $archiveUrl"
Write-Output "Destination: $componentRoot"

if ($DryRun) {
    Write-Output "Dry run complete. No files were downloaded."
    exit 0
}

if (Test-Path $componentRoot) {
    throw "Destination already exists: $componentRoot"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("velo-tomcat-import-" + [System.Guid]::NewGuid())
New-Item -ItemType Directory -Path $tempRoot | Out-Null

try {
    $archivePath = Join-Path $tempRoot "apache-tomcat-$Version-src.tar.gz"
    $extractRoot = Join-Path $tempRoot "extract"
    New-Item -ItemType Directory -Path $extractRoot | Out-Null

    Invoke-WebRequest -Uri $archiveUrl -OutFile $archivePath
    tar -xzf $archivePath -C $extractRoot

    $expandedSourceRoot = Join-Path $extractRoot "apache-tomcat-$Version-src"
    if (-not (Test-Path $expandedSourceRoot)) {
        throw "Extracted Tomcat source root not found: $expandedSourceRoot"
    }

    New-Item -ItemType Directory -Path (Split-Path -Parent $componentRoot) -Force | Out-Null
    Move-Item -Path $expandedSourceRoot -Destination $componentRoot

    New-Item -ItemType Directory -Path $patchRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $adapterRoot -Force | Out-Null

    $manifest = Get-Content -Path $manifestPath -Raw | ConvertFrom-Json
    if ($null -eq $manifest.sources) {
        $manifest | Add-Member -MemberType NoteProperty -Name sources -Value @()
    }

    $relativePath = "vendor/upstream/apache-tomcat/$Version"
    $existing = @($manifest.sources | Where-Object { $_.path -eq $relativePath })
    if ($existing.Count -eq 0) {
        $manifest.sources += [PSCustomObject]@{
            component = "apache-tomcat"
            version = $Version
            path = $relativePath
            license = "Apache-2.0"
            upstreamTag = $Version
            upstreamUrl = $archiveUrl
            noticeRequired = $true
            importedAt = (Get-Date -Format "yyyy-MM-dd")
        }
    }

    $manifest.lastUpdated = Get-Date -Format "yyyy-MM-dd"
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -Path $manifestPath -Encoding UTF8

    Write-Output "Imported Apache Tomcat source to $relativePath"
    Write-Output "Run scripts/check-oss-compliance.ps1 next to validate LICENSE/NOTICE retention."
} finally {
    if (Test-Path $tempRoot) {
        Remove-Item -Path $tempRoot -Recurse -Force
    }
}
