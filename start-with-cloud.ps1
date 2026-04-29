# IoT Monitor - Start with Supabase Cloud Storage
# This script sets up environment variables and starts the backend

Write-Host "Setting up Supabase environment variables..." -ForegroundColor Green

# Set Supabase credentials
$env:SUPABASE_URL = "https://oavizsbcurtnsekuwebj.supabase.co"
$env:SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9hdml6c2JjdXJ0bnNla3V3ZWJqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM4MTY3NzgsImV4cCI6MjA4OTM5Mjc3OH0.ZF8Nrc8RhgYe-81_WFPmsCjDSxcTPFmn4N4TEgd3AFU"

Write-Host "Environment variables set successfully!" -ForegroundColor Green
Write-Host "SUPABASE_URL: $env:SUPABASE_URL" -ForegroundColor Yellow
Write-Host ""
Write-Host "Starting IoT Monitor Backend with cloud storage..." -ForegroundColor Green
Write-Host ""

# Change to backend directory and start the application
Set-Location -Path "backend"
mvn spring-boot:run