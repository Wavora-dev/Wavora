@echo off
REM Wavora - instalador de Windows, un solo doble clic.
REM Tiene que estar en la MISMA carpeta que install.ps1, wavora.crt
REM y el .msix (los trae el zip AppwavoraWindows.zip).

REM Pide permisos de administrador si todavia no los tiene
REM (hacen falta para instalar el certificado como raiz confiable).
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Se necesitan permisos de administrador, abriendo ventana nueva...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"

echo.
echo Instalando Wavora...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "install.ps1"

echo.
echo Listo. Si la app no abrio sola, busca "Wavora" en el menu de inicio.
pause
