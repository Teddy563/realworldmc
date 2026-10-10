@echo off
cd /d "%~dp0service"
node --max-old-space-size=8192 src\server.mjs
pause
