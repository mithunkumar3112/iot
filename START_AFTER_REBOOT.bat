@echo off
REM IoT Monitor - Auto-Start Script After System Reboot
REM This script starts MySQL and the backend server

setlocal enabledelayedexpansion

echo.
echo ============================================
echo  IoT Monitor - Auto-Start Script
echo ============================================
echo.

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo [1/4] Checking if MySQL is running...
tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo [OK] MySQL is already running
) else (
    echo [INFO] Starting MySQL service...
    sc query MySQL80 >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        net start MySQL80 >nul 2>&1
        echo [OK] MySQL service started
    ) else (
        echo [WARNING] MySQL80 service not found. Trying to start mysqld.exe directly...
        start "" "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe" --defaults-file="C:\ProgramData\MySQL\MySQL Server 8.0\my.ini"
        echo [INFO] MySQL starting in background...
    )
)

echo.
echo [2/4] Waiting for MySQL to be ready (5 seconds)...
timeout /t 5 /nobreak

echo.
echo [3/4] Testing MySQL connection...
mysql -u root -ppassword -e "SELECT 1" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo [OK] MySQL is responding
) else (
    echo [WARNING] MySQL connection failed - it may take longer to start
    echo [INFO] Waiting additional 10 seconds...
    timeout /t 10 /nobreak
)

echo.
echo [4/4] Starting IoT Monitor Backend Server...
echo.
echo Backend will start in a moment. This window will stay open to show logs.
echo The server should be ready in 10-20 seconds.
echo.
echo To access the files:
echo   URL: http://localhost:8080/dashboard.html
echo.
echo To stop the server: Press Ctrl+C
echo ============================================
echo.

cd /d "%SCRIPT_DIR%backend"

REM Start Maven Spring Boot
call mvn spring-boot:run

pause
