@echo off
REM HDR Viewer portable env: JDK, Android SDK, embedded Python under misc\tools
REM Usage from any cwd:  call "%PATH_TO_REPO%\misc\env_portable.cmd"

set "HDRV_MISC=%~dp0"
if "%HDRV_MISC:~-1%"=="\" set "HDRV_MISC=%HDRV_MISC:~0,-1%"

pushd "%HDRV_MISC%\.." >nul || exit /b 1
set "HDRV_ROOT=%CD%"
popd >nul

set "JAVA_HOME=%HDRV_ROOT%\misc\tools\jdk-17"
set "ANDROID_SDK_ROOT=%HDRV_ROOT%\misc\tools\android-sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "PYTHON_HOME=%HDRV_ROOT%\misc\tools\python"

set "HDRV_ANDROID=%HDRV_ROOT%\android"
set "PATH=%PYTHON_HOME%;%PYTHON_HOME%\Scripts;%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

REM Optional: Gradle cache inside repo (uncomment; increases disk use)
REM set "GRADLE_USER_HOME=%HDRV_ROOT%\.gradle-user"

echo [HDR Viewer] HDRV_ROOT=%HDRV_ROOT%
echo [HDR Viewer] HDRV_ANDROID=%HDRV_ANDROID%
echo [HDR Viewer] JAVA_HOME=%JAVA_HOME%
echo [HDR Viewer] ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT%
