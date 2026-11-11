@echo off
rem Starts the PRISALY API (app module) in the foreground. Ctrl+C stops it.
rem From Git Bash run:  cmd //c iniciar-backend.cmd
rem Configuration is read from environment variables; .env only fills variables that are not already set.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar-backend.ps1" %*
exit /b %errorlevel%
