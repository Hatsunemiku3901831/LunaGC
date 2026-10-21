@echo off
setlocal
set "CARGO_HOME=%~dp0local\cargo"
set "RUSTUP_HOME=%~dp0local\rustup"
set "GRADLE_USER_HOME=%~dp0local\gradle"
for /d %%J in ("%~dp0local\runtime\jdk-17*") do set "JAVA_HOME=%%~fJ"
set "PATH=%JAVA_HOME%\bin;%CARGO_HOME%\bin;%~dp0local\runtime\task;%PATH%"
if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe" set "PATH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd;%PATH%"
if not exist "%~dp0local\runtime\task\task.exe" (
    echo Portable Task is missing under local\runtime\task.
    exit /b 1
)
pushd "%~dp0"
"%~dp0local\runtime\task\task.exe" %*
set "TASK_EXIT=%ERRORLEVEL%"
popd
exit /b %TASK_EXIT%
