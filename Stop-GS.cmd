@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\local_windows.ps1" -Mode stop
if errorlevel 1 pause
