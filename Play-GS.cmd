@echo off
rem Ultrawide: 3840x1645, normal window.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\local_windows.ps1" -Mode play -GameWidth 3840 -GameHeight 1645 %*
if errorlevel 1 pause
