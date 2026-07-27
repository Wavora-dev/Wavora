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
copy /y "%~dp0uninstall-wavora.bat" "%BUNDLE%\" >nul
copy /y "%~dp0uninstall.ps1" "%BUNDLE%\" >nul
copy /y "%OUTPUT%\wavora.crt" "%BUNDLE%\" >nul
copy /y "%OUTPUT%\wavora.exe" "%BUNDLE%\" >nul

REM install.ps1 espera wavora.ico junto a si mismo (icono del acceso
REM directo de escritorio - ver AUDIT NOTE en install.ps1). Antes esto
REM habia que copiarlo y renombrarlo a mano; ahora queda automatizado.
if not exist "%~dp0..\..\composeApp\icon\circle_app_icon.ico" (
    echo No se encontro composeApp\icon\circle_app_icon.ico
    pause
    exit /b 1
)
copy /y "%~dp0..\..\composeApp\icon\circle_app_icon.ico" "%BUNDLE%\wavora.ico" >nul

set MSIX_COUNT=0
for %%F in ("%OUTPUT%\*.msix") do set /a MSIX_COUNT+=1
if not "%MSIX_COUNT%"=="1" (
    echo Se esperaba exactamente 1 .msix en %OUTPUT%, se encontraron %MSIX_COUNT%.
    echo Borra los .msix viejos de output\ antes de correr este script de nuevo.
    pause
    exit /b 1
)

for %%F in ("%OUTPUT%\*.msix") do copy /y "%%F" "%BUNDLE%\" >nul

if exist "%OUTPUT%\AppwavoraWindows.zip" del "%OUTPUT%\AppwavoraWindows.zip"

powershell -NoProfile -Command "Compress-Archive -Path '%BUNDLE%' -DestinationPath '%OUTPUT%\AppwavoraWindows.zip' -Force"

rmdir /s /q "%BUNDLE%"

echo.
echo Listo: %OUTPUT%\AppwavoraWindows.zip
dir "%OUTPUT%\AppwavoraWindows.zip"
pause
