@echo off
title RAG Document Assistant - Stop
cd /d "%~dp0"

echo.
echo  Stopping RAG Document Assistant...
docker compose stop
echo.
echo  Stopped. Your documents and data are kept. Double-click start.bat to run it again.
timeout /t 5
