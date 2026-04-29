@echo off
REM IoT Monitor - Start with Supabase Cloud Storage
REM This script sets up environment variables and starts the backend

echo Setting up Supabase environment variables...

REM Set Supabase credentials
set SUPABASE_URL=https://oavizsbcurtnsekuwebj.supabase.co
set SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9hdml6c2JjdXJ0bnNla3V3ZWJqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM4MTY3NzgsImV4cCI6MjA4OTM5Mjc3OH0.ZF8Nrc8RhgYe-81_WFPmsCjDSxcTPFmn4N4TEgd3AFU

echo Environment variables set successfully!
echo SUPABASE_URL: %SUPABASE_URL%
echo.
echo Starting IoT Monitor Backend with cloud storage...
echo.

REM Change to backend directory and start the application
cd backend
mvn spring-boot:run