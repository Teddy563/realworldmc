@echo off
cd /d "%~dp0service"
node src/streetview-server.mjs
if errorlevel 1 pause
