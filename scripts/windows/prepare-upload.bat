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
REM (Android-Wavora-arm64.apk, Android-Wavora-armeabi-v7a.apk,
REM Android-Wavora-x86_64.apk, Android-Wavora-universal.apk, y sus
REM equivalentes Android-Wavora-TV-*.apk) estan tomados directo de
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
REM Desktop (Windows): todo lo que hay en output\, salvo lo que no hace
REM falta subir a un Release:
REM   - install.ps1        (ver AUDIT NOTE mas abajo)
REM   - download.html       (pagina de landing de Conveyor - la propia doc
REM                          de Conveyor dice explicitamente que se puede
REM                          omitir al subir a GitHub Releases)
REM   - icon*.png/.ico/.icns (imagenes que Conveyor genera para esa misma
REM                          pagina de landing - no las usa ni el instalador
REM                          ni el auto-update, solo el download.html)
REM   - launch.*             (scripts de bootstrap por plataforma que genera
REM                          Conveyor para "make site" - Wavora solo compila
REM                          windows.amd64 en este flujo, asi que salen con
REM                          URLs vacias y no sirven para nada; ver conveyor.conf
REM                          si en algun momento se agrega mac/linux de verdad)
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
        set "SKIP=0"
        set "FN=%%~nxF"
        if /I "%%~nxF"=="install.ps1" set "SKIP=1"
        if /I "%%~nxF"=="download.html" set "SKIP=1"
        if /I "!FN:~0,4!"=="icon" set "SKIP=1"
        if /I "!FN:~0,7!"=="launch." set "SKIP=1"
        if "!SKIP!"=="0" (
            copy /y "%%F" "%UPLOAD%\" >nul
            echo   [OK] %%~nxF
            if /I "%%~xF"==".msix" set "MSIX_FOUND=1"
            if /I "%%~xF"==".appinstaller" set "APPINSTALLER_FOUND=1"
        ) else (
            echo   [SKIP] %%~nxF (no hace falta en el Release)
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
REM renombrados con el prefijo Android-Wavora-. Se descarta cualquier ruta
REM que contenga "unsigned" (APK sin firmar, no sirve para subir) o "\tv\"
REM (ese es el de Android TV, se busca aparte mas abajo).
REM ---------------------------------------------------------------------
echo.
echo --- Android (mobile) ---
set "ANDROID_OUT=%ROOT%\androidApp\build\outputs\apk"

call :find_apk "arm64-v8a"      "Android-Wavora-arm64.apk"          "0"
call :find_apk "armeabi-v7a"    "Android-Wavora-armeabi-v7a.apk"    "0"
call :find_apk "x86_64"         "Android-Wavora-x86_64.apk"         "0"
call :find_apk "universal"      "Android-Wavora-universal.apk"      "0"

REM ---------------------------------------------------------------------
REM Android TV: mismos 4 ABIs que mobile (el splits{abi{}} de
REM buildTypes.release no es especifico de flavor, tv genera los mismos 4
REM APKs). Reutiliza :find_apk en modo TV (busca DENTRO de "\tv\" en vez de
REM excluirlo) y conserva "-TV-" en el nombre final a proposito: es lo que
REM usa UpdateRepositoryImpl.kt para ignorar estos assets al buscar
REM actualizaciones desde la app de celular (ver comentario en
REM androidApp\build.gradle.kts).
REM ---------------------------------------------------------------------
echo.
echo --- Android TV ---

call :find_apk "arm64-v8a"      "BETA-Android-Wavora-TV-arm64.apk"        "1"
call :find_apk "armeabi-v7a"    "BETA-Android-Wavora-TV-armeabi-v7a.apk"  "1"
call :find_apk "x86_64"         "BETA-Android-Wavora-TV-x86_64.apk"       "1"
call :find_apk "universal"      "BETA-Android-Wavora-TV-universal.apk"    "1"

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

REM %1 = ABI a buscar (ej. "arm64-v8a")
REM %2 = nombre final del archivo en upload\
REM %3 = "1" para buscar el flavor TV (dentro de "\tv\"), "0" para mobile
REM      (fuera de "\tv\")
:find_apk
setlocal
set "ABI=%~1"
set "TARGET_NAME=%~2"
set "TV_MODE=%~3"
set "FOUND=0"
if exist "%ANDROID_OUT%" (
    if "%TV_MODE%"=="1" (
        for /f "delims=" %%F in ('dir /s /b "%ANDROID_OUT%\*%ABI%*release*.apk" 2^>nul ^| findstr /I "\\tv\\" ^| findstr /I /V "unsigned"') do (
            if "!FOUND!"=="0" (
                copy /y "%%F" "%UPLOAD%\%TARGET_NAME%" >nul
                echo   [OK] %TARGET_NAME%  (de %%~nxF^)
                set "FOUND=1"
            )
        )
    ) else (
        for /f "delims=" %%F in ('dir /s /b "%ANDROID_OUT%\*%ABI%*release*.apk" 2^>nul ^| findstr /I /V "unsigned" ^| findstr /I /V "\\tv\\"') do (
            if "!FOUND!"=="0" (
                copy /y "%%F" "%UPLOAD%\%TARGET_NAME%" >nul
                echo   [OK] %TARGET_NAME%  (de %%~nxF^)
                set "FOUND=1"
            )
        )
    )
)
endlocal & if "%FOUND%"=="0" (
    echo   [FALTA] No se encontro APK para ABI "%ABI%" ^(TV_MODE=%TV_MODE%^) bajo %ANDROID_OUT%
    set /a MISSING+=1
)
exit /b 0
