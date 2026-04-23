[CmdletBinding()]
param(
    [string]$RepoPath = ".",
    [string]$GradleTask = ":app:assembleDebug",
    [string]$ApkRelativePath = "app/build/outputs/apk/debug/app-debug.apk",
    [switch]$Clean,
    [switch]$NoDaemon
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-FullJdk {
    param([string]$JdkHome)

    if ([string]::IsNullOrWhiteSpace($JdkHome)) {
        return $false
    }

    $javaExe = Join-Path $JdkHome "bin\\java.exe"
    $jlinkExe = Join-Path $JdkHome "bin\\jlink.exe"
    return (Test-Path -LiteralPath $javaExe) -and (Test-Path -LiteralPath $jlinkExe)
}

function Get-JavaMajorVersion {
    param([string]$JdkHome)

    $releaseFile = Join-Path $JdkHome "release"
    if (-not (Test-Path -LiteralPath $releaseFile)) {
        return 0
    }

    $versionLine = Get-Content -LiteralPath $releaseFile | Where-Object { $_ -like "JAVA_VERSION=*" } | Select-Object -First 1
    if (-not $versionLine) {
        return 0
    }

    $versionText = ($versionLine -split "=", 2)[1].Trim('"')
    $parts = $versionText.Split(".")
    if ($parts[0] -eq "1" -and $parts.Length -gt 1) {
        return [int]$parts[1]
    }

    return [int]$parts[0]
}

function Get-CandidateJdkHomes {
    $candidates = New-Object System.Collections.Generic.List[string]

    foreach ($value in @($env:JAVA_HOME, $env:JDK_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            $candidates.Add($value)
        }
    }

    foreach ($path in @(
        "C:\\Program Files\\Java",
        "C:\\Program Files\\Eclipse Adoptium",
        "C:\\Program Files\\Microsoft",
        "C:\\Program Files\\Android\\Android Studio\\jbr",
        "$env:LOCALAPPDATA\\Programs\\Android Studio\\jbr"
    )) {
        if (Test-Path -LiteralPath $path) {
            if (Test-FullJdk -JdkHome $path) {
                $candidates.Add($path)
            }

            Get-ChildItem -LiteralPath $path -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $candidates.Add($_.FullName)
            }
        }
    }

    $jetBrainsRoot = "C:\\Program Files\\JetBrains"
    if (Test-Path -LiteralPath $jetBrainsRoot) {
        Get-ChildItem -LiteralPath $jetBrainsRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $jbrPath = Join-Path $_.FullName "jbr"
            if (Test-Path -LiteralPath $jbrPath) {
                $candidates.Add($jbrPath)
            }
        }
    }

    $seen = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        $trimmed = $candidate.Trim()
        if ($seen.Add($trimmed)) {
            $trimmed
        }
    }
}

function Select-JdkHome {
    $validJdks = foreach ($candidate in Get-CandidateJdkHomes) {
        if (-not (Test-FullJdk -JdkHome $candidate)) {
            continue
        }

        $major = Get-JavaMajorVersion -JdkHome $candidate
        if ($major -lt 17) {
            continue
        }

        [pscustomobject]@{
            JdkHome = $candidate
            Major   = $major
        }
    }

    if (-not $validJdks) {
        throw "No full JDK >= 17 was found. Install a JDK that includes jlink.exe."
    }

    $java17 = $validJdks | Where-Object { $_.Major -eq 17 } | Select-Object -First 1
    if ($java17) {
        return $java17.JdkHome
    }

    return ($validJdks | Select-Object -First 1).JdkHome
}

$repoRoot = (Resolve-Path -LiteralPath $RepoPath).Path
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradleWrapper)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}

$selectedJavaHome = Select-JdkHome
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH

$gradleArgs = New-Object System.Collections.Generic.List[string]
if ($Clean) {
    $gradleArgs.Add("clean")
}
$gradleArgs.Add($GradleTask)
$gradleArgs.Add("--console=plain")
if ($NoDaemon) {
    $gradleArgs.Add("--no-daemon")
}

Push-Location $repoRoot
try {
    $env:JAVA_HOME = $selectedJavaHome
    $env:PATH = "$selectedJavaHome\\bin;$previousPath"

    Write-Host "Resolved JAVA_HOME: $selectedJavaHome"
    Write-Host "Running: .\\gradlew.bat $($gradleArgs -join ' ')"

    & $gradleWrapper @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle exited with code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
}

$apkPath = Join-Path $repoRoot $ApkRelativePath
if (-not (Test-Path -LiteralPath $apkPath)) {
    throw "APK not found at $apkPath after successful build"
}

$apk = Get-Item -LiteralPath $apkPath
Write-Host "APK: $($apk.FullName)"
Write-Host "SizeBytes: $($apk.Length)"
Write-Host "LastWriteTime: $($apk.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss"))"
