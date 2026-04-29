# IoT Monitor - Auto-Start Script (PowerShell)
# Run this with: powershell -ExecutionPolicy Bypass -File START_AFTER_REBOOT.ps1

$ScriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptPath

Write-Host "`n============================================"
Write-Host " IoT Monitor - Auto-Start Script"
Write-Host "============================================`n"

Write-Host "[1/4] Checking if MySQL is running..."
$MySQLProcess = Get-Process mysqld -ErrorAction SilentlyContinue

if ($MySQLProcess) {
    Write-Host "[OK] MySQL is already running (PID: $($MySQLProcess.Id))"
} else {
    Write-Host "[INFO] Starting MySQL service..."
    
    # Try to start MySQL service
    $Service = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
    
    if ($Service) {
        if ($Service.Status -eq "Stopped") {
            Start-Service -Name "MySQL80" -ErrorAction SilentlyContinue
            Write-Host "[OK] MySQL80 service started"
        } else {
            Write-Host "[OK] MySQL80 service is running"
        }
    } else {
        Write-Host "[INFO] MySQL service not found, trying manual start..."
        $MySQLPath = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe"
        
        if (Test-Path $MySQLPath) {
            & $MySQLPath --defaults-file="C:\ProgramData\MySQL\MySQL Server 8.0\my.ini" | Out-Null &
            Write-Host "[INFO] MySQL started"
        } else {
            Write-Host "[WARNING] MySQL executable not found at: $MySQLPath"
        }
    }
}

Write-Host "`n[2/4] Waiting for MySQL to be ready (5 seconds)..."
Start-Sleep -Seconds 5

Write-Host "`n[3/4] Testing MySQL connection..."
try {
    $Connection = New-Object MySql.Data.MySqlClient.MySqlConnection
    $Connection.ConnectionString = "server=localhost;uid=root;pwd=password;database=iot_monitor"
    $Connection.Open()
    $Connection.Close()
    Write-Host "[OK] MySQL is responding"
} catch {
    Write-Host "[WARNING] MySQL connection failed, waiting additional 10 seconds..."
    Start-Sleep -Seconds 10
}

Write-Host "`n[4/4] Starting IoT Monitor Backend Server...`n"
Write-Host "Backend will start in a moment. Server should be ready in 10-20 seconds.`n"
Write-Host "To access files: http://localhost:8080/dashboard.html"
Write-Host "============================================`n"

Set-Location "$ScriptPath\backend"

# Start Maven
& mvn spring-boot:run
