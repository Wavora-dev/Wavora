@echo off
REM Arma AppwavoraWindows.zip a partir de output\ (correr esto DESPUES de
REM `conveyor -Kapp.machines=windows.amd64 make site`, desde la raiz del repo).
setlocal

set "BUNDLE=%~dp0..\..\AppwavoraWindows"
set "OUTPUT=%~dp0..\..\output"

if not exist "%OUTPUT%\wavora.crt" (
    echo No se encontro %OUTPUT%\wavora.crt - corriste "conveyor make site" antes?
    pause
    exit /b 1
)

if exist "%BUNDLE%" rmdir /s /q "%BUNDLE%"
mkdir "%BUNDLE%"

copy /y "%~dp0install-wavora.bat" "%BUNDLE%\" >nul
copy /y "%~dp0install.ps1" "%BUNDLE%\" >nul
copy /y "%OUTPUT%\wavora.crt" "%BUNDLE%\" >nul
copy /y "%OUTPUT%\wavora.exe" "%BUNDLE%\" >nul

for %%F in ("%OUTPUT%\*.msix") do copy /y "%%F" "%BUNDLE%\" >nul

if exist "%OUTPUT%\AppwavoraWindows.zip" del "%OUTPUT%\AppwavoraWindows.zip"

powershell -NoProfile -Command "Compress-Archive -Path '%BUNDLE%' -DestinationPath '%OUTPUT%\AppwavoraWindows.zip' -Force"

rmdir /s /q "%BUNDLE%"

echo.
echo Listo: %OUTPUT%\AppwavoraWindows.zip
dir "%OUTPUT%\AppwavoraWindows.zip"
pause
