@echo off
REM ─────────────────────────────────────────────────────────
REM  Velo WAS - Start Server (Windows)
REM ─────────────────────────────────────────────────────────
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."

REM ── Toolchain ────────────────────────────────────────────
if exist "%PROJECT_ROOT%\.tools\jdk\jdk-21.0.10+7" (
    set "JAVA_HOME=%PROJECT_ROOT%\.tools\jdk\jdk-21.0.10+7"
) else if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME is not set.
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "FAT_JAR="
set "CONFIG_FILE=%PROJECT_ROOT%\conf\server.yaml"
set "PID_FILE=%PROJECT_ROOT%\velo-was.pid"
set "LOG_DIR=%PROJECT_ROOT%\logs"
set "DAEMON=false"
set "JVM_OPTS=-Xms256m -Xmx1g -XX:+UseZGC"

if defined VELO_CONFIG set "CONFIG_FILE=%VELO_CONFIG%"
if defined VELO_JVM_OPTS set "JVM_OPTS=%VELO_JVM_OPTS%"

REM ── Parse arguments ──────────────────────────────────────
:parse_start_args
if "%~1"=="" goto :do_start
if /i "%~1"=="-c"        ( set "CONFIG_FILE=%~2"& shift & shift & goto :parse_start_args )
if /i "%~1"=="--config"  ( set "CONFIG_FILE=%~2"& shift & shift & goto :parse_start_args )
if /i "%~1"=="-d"        ( set "DAEMON=true"& shift & goto :parse_start_args )
if /i "%~1"=="--daemon"  ( set "DAEMON=true"& shift & goto :parse_start_args )
if /i "%~1"=="-j"        ( set "JVM_OPTS=%~2"& shift & shift & goto :parse_start_args )
if /i "%~1"=="--jvm-opts" ( set "JVM_OPTS=%~2"& shift & shift & goto :parse_start_args )
if /i "%~1"=="-h"        ( goto :start_usage )
if /i "%~1"=="--help"    ( goto :start_usage )
echo Unknown option: %~1
goto :start_usage

:do_start
REM Check fat jar
call :find_fat_jar
if not defined FAT_JAR (
    echo [WARN] Fat jar not found. Building...
    call "%SCRIPT_DIR%build.bat" -p -q
    if errorlevel 1 exit /b 1
    call :find_fat_jar
)
if not defined FAT_JAR (
    echo [ERROR] Fat jar not found under was-bootstrap\target.
    exit /b 1
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ════════════════════════════════════════════════════════
echo   Velo WAS - Starting
echo   JAVA_HOME : %JAVA_HOME%
echo   Fat Jar   : %FAT_JAR%
echo   Config    : %CONFIG_FILE%
echo   JVM Opts  : %JVM_OPTS%
echo   Mode      : %DAEMON%
echo ════════════════════════════════════════════════════════

if "%DAEMON%"=="true" (
    start "Velo WAS" /B "%JAVA_HOME%\bin\java.exe" %JVM_OPTS% -Dvelo.config="%CONFIG_FILE%" -Dvelo.home="%PROJECT_ROOT%" -jar "%FAT_JAR%" > "%LOG_DIR%\velo-was.out" 2>&1
    echo Server started in background mode.
    echo   Log: %LOG_DIR%\velo-was.out
) else (
    "%JAVA_HOME%\bin\java.exe" %JVM_OPTS% -Dvelo.config="%CONFIG_FILE%" -Dvelo.home="%PROJECT_ROOT%" -jar "%FAT_JAR%"
)
exit /b 0

:find_fat_jar
set "FAT_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o:-d "%PROJECT_ROOT%\was-bootstrap\target\was-bootstrap-*-jar-with-dependencies.jar" 2^>nul') do (
    if not defined FAT_JAR set "FAT_JAR=%PROJECT_ROOT%\was-bootstrap\target\%%F"
)
exit /b 0

:start_usage
echo Usage: start.bat [OPTIONS]
echo.
echo Options:
echo   -c, --config ^<path^>   Config file (default: conf\server.yaml)
echo   -d, --daemon           Run in background
echo   -j, --jvm-opts ^<opts^>  JVM options
echo   -h, --help             Show this help
echo.
echo Examples:
echo   start.bat                              Foreground start
echo   start.bat -d                           Daemon mode
echo   start.bat -c conf\prod.yaml -d         Custom config
exit /b 0
