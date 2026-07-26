@echo off
REM Wavora - desinstalador de Windows, un solo doble clic.
REM Tiene que estar en la MISMA carpeta que uninstall.ps1.

REM Pide permisos de administrador si todavia no los tiene
REM (hacen falta para Remove-AppxPackage -AllUsers).
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Se necesitan permisos de administrador, abriendo ventana nueva...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"

echo.
echo Desinstalando Wavora...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "uninstall.ps1"

echo.
echo Listo.
pause
