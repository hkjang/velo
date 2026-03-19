param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$ReportPath = "third_party/reports/oss-compliance-report.md"
)

$ErrorActionPreference = "Stop"

function Get-RelativeRepoPath {
    param(
        [string]$Root,
        [string]$Path
    )

    $resolvedRoot = [System.IO.Path]::GetFullPath($Root)
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)

    if ($resolvedPath.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $resolvedPath.Substring($resolvedRoot.Length).TrimStart('\').Replace('\', '/')
    }

    return $resolvedPath.Replace('\', '/')
}

function Get-PolicyMatch {
    param(
        [object[]]$Entries,
        [string]$GroupId,
        [string]$ArtifactId
    )

    foreach ($entry in $Entries) {
        $groupMatches = ($entry.groupId -eq "*") -or ($entry.groupId -eq $GroupId)
        $artifactMatches = ($entry.artifactId -eq "*") -or ($entry.artifactId -eq $ArtifactId)
        if ($groupMatches -and $artifactMatches) {
            return $entry
        }
    }

    return $null
}

function New-UniqueList {
    return New-Object System.Collections.ArrayList
}

function Add-UniqueValue {
    param(
        [object]$List,
        [string]$Value
    )

    if ($null -eq $List) {
        return
    }

    if (-not [string]::IsNullOrWhiteSpace($Value) -and -not $List.Contains($Value)) {
        [void]$List.Add($Value)
    }
}

$requiredFiles = @(
    "NOTICE",
    "THIRD_PARTY.md",
    "docs/ko/oss-reuse-policy.md",
    "vendor/README.md",
    "third_party/dependency-policy.json",
    "third_party/reuse-manifest.json"
)

$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

foreach ($requiredFile in $requiredFiles) {
    $fullPath = Join-Path $RepoRoot $requiredFile
    if (-not (Test-Path $fullPath)) {
        $errors.Add("Missing required governance file: $requiredFile")
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

$dependencyPolicyPath = Join-Path $RepoRoot "third_party/dependency-policy.json"
$dependencyPolicy = Get-Content -Path $dependencyPolicyPath -Raw | ConvertFrom-Json
$allowedLicenses = @($dependencyPolicy.allowedLicenses)
$policyEntries = @($dependencyPolicy.dependencies)

$reuseManifestPath = Join-Path $RepoRoot "third_party/reuse-manifest.json"
$reuseManifest = Get-Content -Path $reuseManifestPath -Raw | ConvertFrom-Json
$manifestEntries = @{}

foreach ($source in @($reuseManifest.sources)) {
    $sourcePath = [string]$source.path
    if ([string]::IsNullOrWhiteSpace($sourcePath)) {
        $errors.Add("Vendored source entry is missing a path.")
        continue
    }

    $manifestEntries[$sourcePath.Replace('\', '/')] = $source

    $fullSourcePath = Join-Path $RepoRoot $sourcePath
    if (-not (Test-Path $fullSourcePath)) {
        $errors.Add("Vendored source path listed in manifest does not exist: $sourcePath")
        continue
    }

    if ([string]::IsNullOrWhiteSpace([string]$source.upstreamUrl)) {
        $errors.Add("Vendored source entry is missing upstreamUrl: $sourcePath")
    }

    if ([string]::IsNullOrWhiteSpace([string]$source.upstreamTag)) {
        $errors.Add("Vendored source entry is missing upstreamTag: $sourcePath")
    }

    $license = [string]$source.license
    if (-not ($allowedLicenses -contains $license)) {
        $errors.Add("Vendored source has a non-allowlisted license '$license': $sourcePath")
    }

    $licenseFile = Join-Path $fullSourcePath "LICENSE"
    if (-not (Test-Path $licenseFile)) {
        $errors.Add("Vendored source is missing LICENSE: $sourcePath")
    }

    if ($source.noticeRequired -eq $true) {
        $noticeFile = Join-Path $fullSourcePath "NOTICE"
        if (-not (Test-Path $noticeFile)) {
            $errors.Add("Vendored Apache source is missing NOTICE: $sourcePath")
        }
    }
}

$vendorUpstreamRoot = Join-Path $RepoRoot "vendor/upstream"
if (Test-Path $vendorUpstreamRoot) {
    $componentDirs = Get-ChildItem -Path $vendorUpstreamRoot -Directory
    foreach ($componentDir in $componentDirs) {
        $versionDirs = Get-ChildItem -Path $componentDir.FullName -Directory
        foreach ($versionDir in $versionDirs) {
            $relativePath = Get-RelativeRepoPath -Root $RepoRoot -Path $versionDir.FullName
            if (-not $manifestEntries.ContainsKey($relativePath)) {
                $errors.Add("Vendored source directory is not registered in reuse-manifest.json: $relativePath")
            }
        }
    }
}

$excludedPomPattern = "\\deploy\\\.work\\|\\target\\|\\\.tools\\|\\vendor\\upstream\\"
$pomFiles = Get-ChildItem -Path $RepoRoot -Recurse -Filter pom.xml -File |
    Where-Object { $_.FullName -notmatch $excludedPomPattern }

$dependencies = @{}

foreach ($pomFile in $pomFiles) {
    [xml]$pom = Get-Content -Path $pomFile.FullName
    $moduleName = Split-Path -Leaf (Split-Path -Parent $pomFile.FullName)
    $dependencyNodes = @($pom.project.dependencies.dependency)

    foreach ($dependencyNode in $dependencyNodes) {
        if ($null -eq $dependencyNode) {
            continue
        }

        $groupId = [string]$dependencyNode.groupId
        $artifactId = [string]$dependencyNode.artifactId
        if ([string]::IsNullOrWhiteSpace($groupId) -or [string]::IsNullOrWhiteSpace($artifactId)) {
            continue
        }

        if ($groupId -eq "io.velo.was") {
            continue
        }

        $scope = [string]$dependencyNode.scope
        if ([string]::IsNullOrWhiteSpace($scope)) {
            $scope = "compile"
        }

        $version = [string]$dependencyNode.version
        if ([string]::IsNullOrWhiteSpace($version)) {
            $version = "(managed)"
        }

        $key = "{0}:{1}" -f $groupId, $artifactId
        if (-not $dependencies.ContainsKey($key)) {
            $dependencies[$key] = [PSCustomObject]@{
                key = $key
                groupId = $groupId
                artifactId = $artifactId
                versions = (New-UniqueList)
                scopes = (New-UniqueList)
                modules = (New-UniqueList)
            }
        }

        Add-UniqueValue -List $dependencies[$key].versions -Value $version
        Add-UniqueValue -List $dependencies[$key].scopes -Value $scope
        Add-UniqueValue -List $dependencies[$key].modules -Value $moduleName
    }
}

$dependencyRows = New-Object System.Collections.Generic.List[object]

foreach ($dependency in $dependencies.Values | Sort-Object key) {
    $policyMatch = Get-PolicyMatch -Entries $policyEntries -GroupId $dependency.groupId -ArtifactId $dependency.artifactId
    if ($null -eq $policyMatch) {
        $errors.Add("Dependency is not registered in third_party/dependency-policy.json: $($dependency.key)")
        $dependencyRows.Add([PSCustomObject]@{
            Dependency = $dependency.key
            Scopes = ($dependency.scopes -join ", ")
            Modules = ($dependency.modules -join ", ")
            Status = "unregistered"
            License = ""
            Notes = ""
        })
        continue
    }

    $status = [string]$policyMatch.status
    $license = [string]$policyMatch.license
    $notes = [string]$policyMatch.notes

    if ($status -eq "allowed" -and -not ($allowedLicenses -contains $license)) {
        $errors.Add("Allowed dependency must use an allowlisted license: $($dependency.key) -> $license")
    }

    if ($status -eq "blocked") {
        $errors.Add("Blocked dependency is present in the build: $($dependency.key)")
    }

    if ($status -eq "review-required") {
        $warnings.Add("Dependency requires legal review under Apache/MIT-only policy: $($dependency.key) -> $license")
    }

    $dependencyRows.Add([PSCustomObject]@{
        Dependency = $dependency.key
        Scopes = ($dependency.scopes -join ", ")
        Modules = ($dependency.modules -join ", ")
        Status = $status
        License = $license
        Notes = $notes
    })
}

$reportFullPath = Join-Path $RepoRoot $ReportPath
$reportDirectory = Split-Path -Parent $reportFullPath
if (-not (Test-Path $reportDirectory)) {
    New-Item -ItemType Directory -Path $reportDirectory | Out-Null
}

$reportLines = New-Object System.Collections.Generic.List[string]
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"

$reportLines.Add("# OSS Compliance Report")
$reportLines.Add("")
$reportLines.Add("- Generated: $timestamp")
$reportLines.Add("- Policy file: third_party/dependency-policy.json")
$reportLines.Add("- Reuse manifest: third_party/reuse-manifest.json")
$reportLines.Add("")
$reportLines.Add("## Direct dependencies")
$reportLines.Add("")
$reportLines.Add("| Dependency | Scopes | Modules | Status | License | Notes |")
$reportLines.Add("|---|---|---|---|---|---|")

foreach ($row in $dependencyRows | Sort-Object Dependency) {
    $notes = ($row.Notes -replace "\|", "/")
    $reportLines.Add("| $($row.Dependency) | $($row.Scopes) | $($row.Modules) | $($row.Status) | $($row.License) | $notes |")
}

$reportLines.Add("")
$reportLines.Add("## Vendored sources")
$reportLines.Add("")

if (@($reuseManifest.sources).Count -eq 0) {
    $reportLines.Add("- No vendored upstream source trees are registered.")
} else {
    $reportLines.Add("| Path | License | Upstream tag | NOTICE required |")
    $reportLines.Add("|---|---|---|---|")
    foreach ($source in @($reuseManifest.sources)) {
        $reportLines.Add("| $([string]$source.path) | $([string]$source.license) | $([string]$source.upstreamTag) | $([bool]$source.noticeRequired) |")
    }
}

$reportLines.Add("")
$reportLines.Add("## Result")
$reportLines.Add("")
$reportLines.Add("- Errors: $($errors.Count)")
$reportLines.Add("- Warnings: $($warnings.Count)")

Set-Content -Path $reportFullPath -Value $reportLines -Encoding UTF8

Write-Output "OSS compliance report written to $(Get-RelativeRepoPath -Root $RepoRoot -Path $reportFullPath)"

if ($warnings.Count -gt 0) {
    $warnings | ForEach-Object { Write-Warning $_ }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Output "OSS compliance validation passed."
