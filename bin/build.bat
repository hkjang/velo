@echo off
REM ─────────────────────────────────────────────────────────
REM  Velo WAS - Build Script (Windows)
REM ─────────────────────────────────────────────────────────
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."

REM ── Toolchain detection ──────────────────────────────────
if exist "%PROJECT_ROOT%\.tools\jdk\jdk-21.0.10+7" (
    set "JAVA_HOME=%PROJECT_ROOT%\.tools\jdk\jdk-21.0.10+7"
) else if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME is not set and local JDK not found.
    exit /b 1
)

if exist "%PROJECT_ROOT%\.tools\maven\apache-maven-3.9.13\bin\mvn.cmd" (
    set "MVN=%PROJECT_ROOT%\.tools\maven\apache-maven-3.9.13\bin\mvn.cmd"
) else (
    where mvn >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] Maven not found.
        exit /b 1
    )
    set "MVN=mvn"
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

REM ── Default values ───────────────────────────────────────
set "GOAL=compile"
set "CLEAN="
set "SKIP_TESTS=-DskipTests"
set "QUIET="
set "PL_OPTION="
set "MODULE_NAMES=all"

REM ── Parse arguments ──────────────────────────────────────
:parse_args
if "%~1"=="" goto :run_build
if /i "%~1"=="-c"           ( set "CLEAN=clean"& shift & goto :parse_args )
if /i "%~1"=="--clean"      ( set "CLEAN=clean"& shift & goto :parse_args )
if /i "%~1"=="-t"           ( set "SKIP_TESTS="& shift & goto :parse_args )
if /i "%~1"=="--test"       ( set "SKIP_TESTS="& shift & goto :parse_args )
if /i "%~1"=="-s"           ( set "SKIP_TESTS=-DskipTests"& shift & goto :parse_args )
if /i "%~1"=="--skip-tests" ( set "SKIP_TESTS=-DskipTests"& shift & goto :parse_args )
if /i "%~1"=="-p"           ( set "GOAL=package"& shift & goto :parse_args )
if /i "%~1"=="--package"    ( set "GOAL=package"& shift & goto :parse_args )
if /i "%~1"=="-q"           ( set "QUIET=-q"& shift & goto :parse_args )
if /i "%~1"=="--quiet"      ( set "QUIET=-q"& shift & goto :parse_args )
if /i "%~1"=="-h"           ( goto :usage )
if /i "%~1"=="--help"       ( goto :usage )

REM Must be a module name
set "MOD=%~1"
if not "!MOD:~0,4!"=="was-" set "MOD=was-!MOD!"
if "!PL_OPTION!"=="" (
    set "PL_OPTION=-pl !MOD! -am"
    set "MODULE_NAMES=!MOD!"
) else (
    set "PL_OPTION=!PL_OPTION!,!MOD!"
    set "MODULE_NAMES=!MODULE_NAMES!, !MOD!"
)
shift
goto :parse_args

:run_build
echo ════════════════════════════════════════════════════════
echo   Velo WAS Build
echo   JAVA_HOME : %JAVA_HOME%
echo   Goal      : %CLEAN% %GOAL%
echo   Modules   : %MODULE_NAMES%
echo   Tests     : %SKIP_TESTS:~0,1%skipped
echo ════════════════════════════════════════════════════════

cd /d "%PROJECT_ROOT%"
"%MVN%" %CLEAN% %GOAL% %SKIP_TESTS% %QUIET% %PL_OPTION%
if errorlevel 1 (
    echo [ERROR] Build failed.
    exit /b 1
)

echo.
echo Build completed successfully.

if "%GOAL%"=="package" (
    set "FAT_JAR=%PROJECT_ROOT%\was-bootstrap\target\was-bootstrap-0.1.0-SNAPSHOT-jar-with-dependencies.jar"
    if exist "!FAT_JAR!" (
        echo   Artifact: !FAT_JAR!
    )
)
exit /b 0

:usage
echo Usage: build.bat [OPTIONS] [MODULE...]
echo.
echo Options:
echo   -c, --clean         Clean before build
echo   -t, --test          Run tests
echo   -s, --skip-tests    Skip tests (default)
echo   -p, --package       Create fat jar
echo   -q, --quiet         Quiet output
echo   -h, --help          Show this help
echo.
echo Modules:
echo   all                 Build all modules (default)
echo   ^<module-name^>       e.g. was-admin, was-webadmin, bootstrap
echo.
echo Examples:
echo   build.bat                         Build all, skip tests
echo   build.bat was-admin               Build was-admin only
echo   build.bat -c -t                   Clean build with tests
echo   build.bat -p                      Package fat jar
exit /b 0
