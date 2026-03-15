@echo off
REM ─────────────────────────────────────────────────────────
REM  Velo WAS - Stop Server (Windows)
REM ─────────────────────────────────────────────────────────
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
set "FORCE=false"

REM ── Parse arguments ──────────────────────────────────────
:parse_stop_args
if "%~1"=="" goto :do_stop
if /i "%~1"=="-f"       ( set "FORCE=true"& shift & goto :parse_stop_args )
if /i "%~1"=="--force"  ( set "FORCE=true"& shift & goto :parse_stop_args )
if /i "%~1"=="-h"       ( goto :stop_usage )
if /i "%~1"=="--help"   ( goto :stop_usage )
shift
goto :parse_stop_args

:do_stop
echo ════════════════════════════════════════════════════════
echo   Velo WAS - Stopping
echo ════════════════════════════════════════════════════════

REM Find Velo WAS process by window title or class name
for /f "tokens=2" %%p in ('tasklist /fi "WINDOWTITLE eq Velo WAS" /fo list 2^>nul ^| findstr /i "PID:"') do (
    set "PID=%%p"
)

REM Fallback: find by jar name
if not defined PID (
    for /f "tokens=1" %%p in ('wmic process where "commandline like '%%was-bootstrap%%jar-with-dependencies%%'" get processid /value 2^>nul ^| findstr "="') do (
        for /f "tokens=2 delims==" %%q in ("%%p") do set "PID=%%q"
    )
)

if not defined PID (
    echo [INFO] No Velo WAS process found.
    exit /b 0
)

echo   Found PID: %PID%

if "%FORCE%"=="true" (
    echo   Force killing...
    taskkill /F /PID %PID% >nul 2>&1
) else (
    echo   Sending graceful shutdown...
    taskkill /PID %PID% >nul 2>&1
    REM Wait up to 30 seconds
    set /a WAIT=0
    :wait_loop
    tasklist /fi "PID eq %PID%" 2>nul | findstr "%PID%" >nul 2>&1
    if errorlevel 1 goto :stopped
    if !WAIT! geq 30 (
        echo   Timeout. Force killing...
        taskkill /F /PID %PID% >nul 2>&1
        goto :stopped
    )
    timeout /t 1 /nobreak >nul
    set /a WAIT+=1
    goto :wait_loop
)

:stopped
echo Server stopped.
exit /b 0

:stop_usage
echo Usage: stop.bat [OPTIONS]
echo.
echo Options:
echo   -f, --force    Force kill immediately
echo   -h, --help     Show this help
exit /b 0
