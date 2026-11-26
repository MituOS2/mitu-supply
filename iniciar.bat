@echo off
setlocal
title TBD.SUPPLY // Instalador y Lanzador Automatico
cd /d "%~dp0"

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] No se encontro PowerShell en este sistema Windows.
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap.ps1" %*
set "EXIT_CODE=%errorlevel%"
if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] El arranque fallo con codigo %EXIT_CODE%. Revisa los mensajes anteriores.
    echo %* | findstr /I /C:"-CheckOnly" /C:"-NoBrowser" /C:"-BuildOnly" >nul
    if errorlevel 1 pause
    exit /b %EXIT_CODE%
)
exit /b 0
