@echo off
rem Ultrawide: 3840x1640, borderless window.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\local_windows.ps1" -Mode play -GameWidth 3840 -GameHeight 1640
if errorlevel 1 pause
