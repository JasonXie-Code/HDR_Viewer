#Requires -Version 5.1
# HDR Viewer — 便携环境（PowerShell）：JDK / Android SDK / 嵌入式 Python
# 用法：  . "g:\HDR_Viewer\misc\env_portable.ps1"

$ErrorActionPreference = "Stop"
$MiscDir = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $MiscDir "..")).Path

$env:HDRV_ROOT = $RepoRoot
$env:HDRV_ANDROID = Join-Path $RepoRoot "android"
$env:JAVA_HOME = Join-Path $RepoRoot "misc\tools\jdk-17"
$env:ANDROID_SDK_ROOT = Join-Path $RepoRoot "misc\tools\android-sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:PYTHON_HOME = Join-Path $RepoRoot "misc\tools\python"

$py = $env:PYTHON_HOME
$pyScripts = Join-Path $py "Scripts"
$javaBin = Join-Path $env:JAVA_HOME "bin"
$adb = Join-Path $env:ANDROID_SDK_ROOT "platform-tools"

$prefix = @($py, $pyScripts, $javaBin, $adb) -join [IO.Path]::PathSeparator
$env:PATH = $prefix + [IO.Path]::PathSeparator + $env:PATH

Write-Host "[HDR Viewer] HDRV_ROOT=$RepoRoot"
Write-Host "[HDR Viewer] HDRV_ANDROID=$($env:HDRV_ANDROID)"
Write-Host "[HDR Viewer] JAVA_HOME=$($env:JAVA_HOME)"
Write-Host "[HDR Viewer] ANDROID_SDK_ROOT=$($env:ANDROID_SDK_ROOT)"
