@echo off
setlocal
title TBD.SUPPLY // Servidor Local + Enlace Publico Cloudflare
cd /d "%~dp0"
call "%~dp0iniciar.bat" -Tunnel %*
exit /b %errorlevel%
