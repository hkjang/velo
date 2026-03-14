$repoRoot = Split-Path -Parent $PSScriptRoot
$javaHome = Join-Path $repoRoot ".tools\jdk\jdk-21.0.10+7"
$mavenHome = Join-Path $repoRoot ".tools\maven\apache-maven-3.9.13"

if (-not (Test-Path $javaHome)) {
    throw "Local JDK not found at $javaHome"
}

if (-not (Test-Path $mavenHome)) {
    throw "Local Maven not found at $mavenHome"
}

$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome
$env:PATH = "$javaHome\bin;$mavenHome\bin;$env:PATH"

Write-Output "JAVA_HOME=$env:JAVA_HOME"
Write-Output "MAVEN_HOME=$env:MAVEN_HOME"
