@echo off
REM Arma la carpeta upload\ en la raiz del repo con TODOS los archivos que
REM hay que subir al Release de GitHub - ni mas ni menos que eso, listos
REM para arrastrar de una sola vez a la pagina de Releases.
REM
REM Corre esto DESPUES de:
REM   1) conveyor -Kapp.machines=windows.amd64 make site
REM   2) scripts\windows\build-zip.bat   (arma output\AppwavoraWindows.zip)
REM   3) tu build/firma habitual de los APKs de Android (mobile + tv)
REM
REM La lista de archivos y los nombres exactos de los APKs de Android
REM (Wavora-arm64.apk, Wavora-armeabi-v7a.apk, Wavora-x86_64.apk,
REM Wavora-universal.apk) estan tomados directo de
REM .github\workflows\release.yml, que es la fuente de verdad real de que
REM es "un release completo" - asi esta carpeta queda igual sin importar
REM si lo subis a mano o via ese workflow.

setlocal enabledelayedexpansion

set "ROOT=%~dp0..\.."
set "OUTPUT=%ROOT%\output"
set "UPLOAD=%ROOT%\upload"
set "MISSING=0"

echo ============================================
echo  Armando %UPLOAD%
echo ============================================

if exist "%UPLOAD%" rmdir /s /q "%UPLOAD%"
mkdir "%UPLOAD%"

REM ---------------------------------------------------------------------
REM Desktop (Windows): todo lo que hay en output\, salvo install.ps1.
REM
REM AUDIT NOTE: el install.ps1 que genera Conveyor en output\ es DISTINTO
REM al scripts\windows\install.ps1 que va DENTRO de AppwavoraWindows.zip
REM (uno funciona basado en red via el .appinstaller, el otro es el
REM instalador local real) - el workflow de CI lo borra a proposito antes
REM de publicar para que nadie lo baje suelto por error. Hacemos lo mismo
REM aca.
REM ---------------------------------------------------------------------
echo.
echo --- Desktop (output\) ---
if not exist "%OUTPUT%" (
    echo   [FALTA] No existe %OUTPUT% - corriste "conveyor make site" y build-zip.bat?
    set /a MISSING+=1
) else (
    if not exist "%OUTPUT%\AppwavoraWindows.zip" (
        echo   [FALTA] AppwavoraWindows.zip - corriste build-zip.bat?
        set /a MISSING+=1
    )
    set "MSIX_FOUND=0"
    set "APPINSTALLER_FOUND=0"
    for %%F in ("%OUTPUT%\*") do (
        if /I not "%%~nxF"=="install.ps1" (
            copy /y "%%F" "%UPLOAD%\" >nul
            echo   [OK] %%~nxF
            if /I "%%~xF"==".msix" set "MSIX_FOUND=1"
            if /I "%%~xF"==".appinstaller" set "APPINSTALLER_FOUND=1"
        )
    )
    if "!MSIX_FOUND!"=="0" (
        echo   [FALTA] Ningun .msix en output\
        set /a MISSING+=1
    )
    if "!APPINSTALLER_FOUND!"=="0" (
        echo   [FALTA] Ningun .appinstaller en output\ - Conveyor no lo genero?
        set /a MISSING+=1
    )
)

REM ---------------------------------------------------------------------
REM Android (mobile): 4 APKs, buscados recursivamente por ABI y
REM renombrados igual que en release.yml. Se descarta cualquier ruta que
REM contenga "unsigned" (APK sin firmar, no sirve para subir) o "\tv\"
REM (ese es el de Android TV, se busca aparte mas abajo).
REM ---------------------------------------------------------------------
echo.
echo --- Android (mobile) ---
set "ANDROID_OUT=%ROOT%\androidApp\build\outputs\apk"

call :find_apk "arm64-v8a"      "Wavora-arm64.apk"
call :find_apk "armeabi-v7a"    "Wavora-armeabi-v7a.apk"
call :find_apk "x86_64"         "Wavora-x86_64.apk"
call :find_apk "universal"      "Wavora-universal.apk"

REM ---------------------------------------------------------------------
REM Android TV: no esta automatizado en release.yml todavia, asi que solo
REM lo buscamos y lo copiamos tal cual lo encontremos (no le garantizamos
REM un nombre final "correcto" - confirma vos que el nombre te sirve antes
REM de subirlo).
REM ---------------------------------------------------------------------
echo.
echo --- Android TV ---
set "TV_FOUND=0"
if exist "%ANDROID_OUT%" (
    for /f "delims=" %%F in ('dir /s /b "%ANDROID_OUT%\*.apk" 2^>nul ^| findstr /I "\\tv\\"') do (
        echo %%F | findstr /I "unsigned" >nul
        if errorlevel 1 (
            copy /y "%%F" "%UPLOAD%\%%~nxF" >nul
            echo   [OK] %%~nxF  (verificá que el nombre te sirva para subir)
            set "TV_FOUND=1"
        )
    )
)
if "!TV_FOUND!"=="0" (
    echo   [FALTA] No se encontro ningun APK de Android TV bajo %ANDROID_OUT%
    echo           Si todavia no lo compilaste, hacelo antes de subir el release.
    set /a MISSING+=1
)

echo.
echo ============================================
if "%MISSING%"=="0" (
    echo  Todo OK - %UPLOAD% esta listo para subir
) else (
    echo  Faltan %MISSING% cosa/s - revisa los [FALTA] de arriba antes de subir
)
echo ============================================
echo.
dir "%UPLOAD%"
pause
exit /b 0

:find_apk
setlocal
set "ABI=%~1"
set "TARGET_NAME=%~2"
set "FOUND=0"
if exist "%ANDROID_OUT%" (
    for /f "delims=" %%F in ('dir /s /b "%ANDROID_OUT%\*%ABI%*release*.apk" 2^>nul ^| findstr /I /V "unsigned" ^| findstr /I /V "\\tv\\"') do (
        if "!FOUND!"=="0" (
            copy /y "%%F" "%UPLOAD%\%TARGET_NAME%" >nul
            echo   [OK] %TARGET_NAME%  (de %%~nxF^)
            set "FOUND=1"
        )
    )
)
endlocal & if "%FOUND%"=="0" (
    echo   [FALTA] No se encontro APK para ABI "%ABI%" bajo %ANDROID_OUT%
    set /a MISSING+=1
)
exit /b 0
