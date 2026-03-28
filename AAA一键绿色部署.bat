@echo off
cd /d "%~dp0"
if not exist "misc\tools\python\python.exe" exit /b 1
"misc\tools\python\python.exe" "misc\green_deploy.py" %*
exit /b %ERRORLEVEL%
