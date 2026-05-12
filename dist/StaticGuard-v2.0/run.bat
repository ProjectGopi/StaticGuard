@echo off
title StaticGuard - Static Code Analyzer

:: ============================================================
::  StaticGuard Launcher (Windows)
::  Usage:
::    run.bat          -> Web UI  (open http://localhost:8080)
::    run.bat --path . -> CLI mode, scan current directory
:: ============================================================

set JAR=StaticGuard.jar

:: Check Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo  ERROR: Java is not installed or not in PATH.
    echo  Please install Java 11 or newer from:
    echo  https://adoptium.net  (free, recommended)
    echo.
    pause
    exit /b 1
)

:: Check JAR exists
if not exist "%JAR%" (
    echo.
    echo  ERROR: %JAR% not found.
    echo  Make sure %JAR% is in the same folder as this script.
    echo.
    pause
    exit /b 1
)

:: If no arguments given, start Web UI
if "%~1"=="" (
    echo.
    echo  Starting StaticGuard Web UI...
    echo  Open your browser at: http://localhost:8080
    echo  Press Ctrl+C to stop.
    echo.
    java -jar "%JAR%" --web
) else (
    :: Pass all arguments to CLI mode
    java -jar "%JAR%" %*
)

pause
