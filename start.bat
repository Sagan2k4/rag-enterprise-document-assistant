@echo off
title RAG Document Assistant - Start
cd /d "%~dp0"

echo.
echo  Starting RAG Document Assistant...
echo.

rem 1. Make sure Docker Desktop is running
docker info >nul 2>&1
if not errorlevel 1 goto dockerready
echo  [1/3] Opening Docker Desktop, please wait...
start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
:waitdocker
ping -n 6 127.0.0.1 >nul
docker info >nul 2>&1
if errorlevel 1 goto waitdocker
:dockerready
echo  [1/3] Docker is running.

rem 2. Start the app (builds it the first time)
echo  [2/3] Starting the app...
docker compose up -d
if errorlevel 1 (
    echo.
    echo  Something went wrong. Check that the .env file has your Gemini key.
    pause
    exit /b 1
)

rem 3. Wait until the backend answers, then open the browser
echo  [3/3] Waiting for the app to be ready...
:waitapp
ping -n 4 127.0.0.1 >nul
curl -s http://localhost:8080/actuator/health | find "UP" >nul
if errorlevel 1 goto waitapp

echo.
echo  Ready! Opening http://localhost:3000
start "" http://localhost:3000
echo.
echo  You can close this window. Double-click stop.bat to shut the app down.
timeout /t 10
