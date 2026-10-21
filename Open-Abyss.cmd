@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\local_windows.ps1" -Mode abyss
if errorlevel 1 pause
