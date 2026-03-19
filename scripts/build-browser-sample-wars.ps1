$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot "samples\browser-wars"
$commonRoot = Join-Path $sourceRoot "common"
$deployRoot = Join-Path $repoRoot "deploy"
$workRoot = Join-Path $repoRoot "work\browser-wars"
$defaultJavaHome = Join-Path $repoRoot ".tools\jdk\jdk-21.0.10+7"
$jarExe = Join-Path ($(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { $defaultJavaHome })) "bin\jar.exe"

if (-not (Test-Path $jarExe)) {
    throw "jar executable not found at $jarExe"
}

if (-not (Test-Path $deployRoot)) {
    New-Item -ItemType Directory -Path $deployRoot | Out-Null
}

if (-not (Test-Path $workRoot)) {
    New-Item -ItemType Directory -Path $workRoot | Out-Null
}

$samples = @(
    @{ Name = "sample-vanilla-js"; Source = "vanilla-js"; Script = "app.js"; TypeScript = $false },
    @{ Name = "sample-typescript"; Source = "typescript"; Script = "app.js"; TypeScript = $true },
    @{ Name = "sample-jquery"; Source = "jquery"; Script = "app.js"; TypeScript = $false },
    @{ Name = "sample-reactjs"; Source = "reactjs"; Script = "app.js"; TypeScript = $false },
    @{ Name = "sample-vuejs"; Source = "vuejs"; Script = "app.js"; TypeScript = $false },
    @{ Name = "sample-angular"; Source = "angular"; Script = "main.js"; TypeScript = $false }
)

function Write-Utf8NoBom([string]$path, [string]$content) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $content, $utf8NoBom)
}

function New-WebXml([string]$displayName) {
    return @"
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0"
         metadata-complete="true">
  <display-name>$displayName</display-name>
  <welcome-file-list>
    <welcome-file>index.html</welcome-file>
  </welcome-file-list>
</web-app>
"@
}

foreach ($sample in $samples) {
    $sampleSourceRoot = Join-Path $sourceRoot $sample.Source
    $stagingRoot = Join-Path $workRoot $sample.Name
    $dataRoot = Join-Path $stagingRoot "data"
    $webInfRoot = Join-Path $stagingRoot "WEB-INF"
    $warFile = Join-Path $deployRoot ($sample.Name + ".war")

    if (Test-Path $stagingRoot) {
        Remove-Item -Recurse -Force $stagingRoot
    }

    New-Item -ItemType Directory -Path $stagingRoot | Out-Null
    New-Item -ItemType Directory -Path $dataRoot | Out-Null
    New-Item -ItemType Directory -Path $webInfRoot | Out-Null

    Copy-Item -Path (Join-Path $commonRoot "showcase.css") -Destination (Join-Path $stagingRoot "showcase.css")
    Copy-Item -Path (Join-Path $commonRoot "dashboard.json") -Destination (Join-Path $dataRoot "dashboard.json")
    Copy-Item -Path (Join-Path $sampleSourceRoot "index.html") -Destination (Join-Path $stagingRoot "index.html")

    if ($sample.TypeScript) {
        $tsSource = Join-Path $sampleSourceRoot "src\app.ts"
        & npm.cmd exec --yes --package=typescript@5.8.2 -- tsc $tsSource --target ES2022 --module ES2022 --outDir $stagingRoot --pretty false
        if ($LASTEXITCODE -ne 0) {
            throw "TypeScript compilation failed for $($sample.Name)"
        }
    } else {
        Copy-Item -Path (Join-Path $sampleSourceRoot $sample.Script) -Destination (Join-Path $stagingRoot $sample.Script)
    }

    Write-Utf8NoBom -path (Join-Path $webInfRoot "web.xml") -content (New-WebXml -displayName $sample.Name)

    if (Test-Path $warFile) {
        Remove-Item -Force $warFile
    }

    & $jarExe --create --file $warFile -C $stagingRoot .
    if ($LASTEXITCODE -ne 0) {
        throw "WAR packaging failed for $($sample.Name)"
    }

    Write-Output "Created $warFile"
}
