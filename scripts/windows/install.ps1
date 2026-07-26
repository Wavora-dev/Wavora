<#
.SYNOPSIS
    Wavora offline Windows installer.

.DESCRIPTION
    Authored, version-controlled installer used for the FIRST INSTALL bundle
    (AppwavoraWindows.zip). Deliberately does NOT go through the
    `.appinstaller` / `Add-AppxPackage -AppInstallerFile <url>` path — that
    path requires Windows' AppInstaller service to successfully resolve and
    download several remote URLs (the .appinstaller manifest, the .msix it
    references, etc.) and has been the source of 0x80190194 / 0x80073CF0 /
    0x800B0109 failures even when the underlying MSIX/certificate are 100%
    valid (confirmed: `Add-AppxPackage <local .msix>` always succeeds).
    Background auto-updates after this first install still use Conveyor's
    own .appinstaller mechanism (see conveyor.conf); this script only
    handles the reliable, fully-local first run.

    Steps:
      1. Elevate to admin if needed (required to trust the cert machine-wide).
      2. Import wavora.crt into LocalMachine\TrustedPeople (idempotent).
      3. Add-AppxPackage the bundled .msix (in-place update if already installed).
      4. Create a Desktop shortcut (MSIX apps only get a Start Menu entry by
         default; Conveyor's Windows MSIX packaging has no desktop-shortcut
         equivalent to compose.desktop's jpackage `shortcut = true`, so we
         create it ourselves here, resolved dynamically via Get-StartApps —
         never hardcode the PackageFamilyName, it changes if the signing key
         or app identity ever changes).
         AUDIT NOTE (generic folder icon bug): a shortcut created with
         TargetPath = explorer.exe has NO icon of its own — Windows resolves
         its icon from the TargetPath executable unless IconLocation is set
         explicitly. Without it, the .lnk showed explorer.exe's own icon
         (a generic folder), even though launching it opened Wavora correctly
         with its real icon in the taskbar/window. Fix: point IconLocation at
         wavora.ico, bundled alongside this script.
         AUDIT NOTE (icon disappears after deleting the installer folder):
         a .lnk's IconLocation only stores a path, not the icon's bytes, so
         pointing it at the extracted zip folder breaks the icon once that
         folder is deleted. Fix: wavora.ico is copied to a persistent
         %LOCALAPPDATA%\Wavora\ folder first, and the shortcut points there
         instead — see the copy step right before shortcut creation below.
      5. Launch the app.
      6. Register a classic Win32 "Uninstall" entry (HKLM\...\Uninstall\Wavora)
         pointing at uninstall.ps1, copiado a la misma carpeta persistente que
         el icono. MSIX no ejecuta ningun codigo propio al desinstalar, asi
         que sin esto Windows solo borra el paquete MSIX y deja huerfanos el
         acceso directo del escritorio y el icono persistente (ver
         uninstall.ps1 para el detalle completo). Esta clave es lo que hace
         que un boton "Desinstalar" real aparezca en Configuracion ->
         Aplicaciones para Wavora, ademas de la entrada nativa del paquete
         MSIX (las dos van a coexistir - ver AUDIT NOTE en el paso 6 mas
         abajo).

    Bundle requirement (same folder as this script):
      - wavora.crt
      - wavora.ico          (usado para el shortcut Y el DisplayIcon del
                              registro de desinstalacion)
      - uninstall.ps1        )  se copian a la carpeta persistente para poder
      - uninstall-wavora.bat )  desinstalar aunque se borre esta carpeta
      - a single *.msix (any name containing "wavora", e.g. wavora-1.1.2.x64.msix)

.NOTES
    Run via install-wavora.bat (handles the UAC elevation prompt), or
    directly: powershell -ExecutionPolicy Bypass -File install.ps1
#>

[CmdletBinding()]
param(
    # Cuando esta presente, no escribe nada a consola y NO pide
    # confirmacion con Read-Host en caso de error (que colgaria para
    # siempre si no hay ninguna consola visible donde escribir esa
    # respuesta). Pensado para invocacion automatizada, ver
    # WavoraUpdater.exe (see wavoraUpdater/.../UpdaterLogic.kt's
    # runInstallScript()). El uso manual (doble click via
    # install-wavora.bat) no pasa este flag y se comporta exactamente
    # igual que antes.
    [switch]$Silent
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

function Write-Step($msg) {
    if ($Silent) { return }
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Write-Info($msg) {
    if ($Silent) { return }
    Write-Host $msg
}

function Fail($msg) {
    if ($Silent) {
        # No hay consola visible para leer un Read-Host - solo salir con
        # codigo de error, que es lo que el caller (WavoraUpdater)
        # efectivamente chequea.
        exit 1
    }
    Write-Host ""
    Write-Host "[ERROR] $msg" -ForegroundColor Red
    Write-Host ""
    Write-Host "Si el problema persiste, reportalo con la salida completa de esta ventana."
    Read-Host "Presiona Enter para cerrar"
    exit 1
}

# --- 0. Verify we're elevated (install-wavora.bat should have handled this,
#        but this script can also be run directly) --------------------------
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Fail "Este script necesita permisos de administrador (para instalar el certificado como raiz confiable). Ejecuta install-wavora.bat en su lugar, o corre PowerShell 'Run as Administrator'."
}

# --- 1. Locate bundle files --------------------------------------------------
Write-Step "Buscando archivos del instalador..."

$cert = Join-Path $scriptDir "wavora.crt"
if (-not (Test-Path $cert)) {
    Fail "No se encontro wavora.crt en $scriptDir"
}

$icon = Join-Path $scriptDir "wavora.ico"

# Carpeta persistente en %LOCALAPPDATA% para todo lo que el shortcut y la
# entrada de desinstalacion necesitan sobrevivir despues de que el usuario
# borre esta carpeta temporal (mismo patron ya usado para WavoraUpdater.exe).
$persistentDir = Join-Path $env:LOCALAPPDATA "Wavora"
if (-not (Test-Path $persistentDir)) {
    New-Item -ItemType Directory -Path $persistentDir -Force | Out-Null
}

if (-not (Test-Path $icon)) {
    Write-Host "  [WARN] No se encontro wavora.ico en $scriptDir - el acceso directo de escritorio quedara sin icono propio." -ForegroundColor Yellow
    $icon = $null
} else {
    # AUDIT NOTE (icono del acceso directo desaparece si se borra la carpeta
    # del instalador): $scriptDir es la carpeta donde el usuario descomprimio
    # AppwavoraWindows.zip (ej. Desktop\wavora-installer\), NO la carpeta de
    # instalacion real de la app (esa es el paquete MSIX, gestionado por
    # Windows). El shortcut.IconLocation de mas abajo, antes, apuntaba
    # directo a "$scriptDir\wavora.ico" - un .lnk solo GUARDA la ruta al
    # archivo de icono, no una copia de sus bytes, asi que si el usuario
    # borra esa carpeta temporal despues de instalar (comportamiento
    # esperable, ya cumplio su proposito), el acceso directo se queda sin
    # icono (Windows no encuentra el .ico y cae al generico). Fix: copiar
    # wavora.ico a $persistentDir y apuntar el shortcut ahi en vez de a
    # $scriptDir - esa carpeta sobrevive aunque se borre el zip extraido.
    $persistentIcon = Join-Path $persistentDir "wavora.ico"
    try {
        Copy-Item -Path $icon -Destination $persistentIcon -Force
        $icon = $persistentIcon
    } catch {
        Write-Host "  [WARN] No se pudo copiar wavora.ico a $persistentDir, se usara la copia temporal: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Mismo problema que el icono: la entrada de desinstalacion que se registra
# mas abajo necesita un UninstallString que apunte a algo persistente, no a
# $scriptDir (que el usuario puede borrar apenas termina de instalar). Se
# copian ahora, junto con el icono, para tenerlos listos.
$persistentUninstallPs1 = Join-Path $persistentDir "uninstall.ps1"
$persistentUninstallBat = Join-Path $persistentDir "uninstall-wavora.bat"
$uninstallScriptsOk = $true
foreach ($pair in @(
    @{ Src = Join-Path $scriptDir "uninstall.ps1"; Dst = $persistentUninstallPs1 },
    @{ Src = Join-Path $scriptDir "uninstall-wavora.bat"; Dst = $persistentUninstallBat }
)) {
    if (-not (Test-Path $pair.Src)) {
        Write-Host "  [WARN] No se encontro $($pair.Src) - no se va a registrar la entrada de desinstalacion en Configuracion -> Aplicaciones." -ForegroundColor Yellow
        $uninstallScriptsOk = $false
        continue
    }
    try {
        Copy-Item -Path $pair.Src -Destination $pair.Dst -Force
    } catch {
        Write-Host "  [WARN] No se pudo copiar $($pair.Src) a $persistentDir : $($_.Exception.Message)" -ForegroundColor Yellow
        $uninstallScriptsOk = $false
    }
}

$msixCandidates = Get-ChildItem -Path $scriptDir -Filter "*.msix" -File |
    Where-Object { $_.Name -like "*wavora*" -or $_.Name -like "*Wavora*" }
if ($msixCandidates.Count -eq 0) {
    # Fall back to any .msix present, in case naming ever changes.
    $msixCandidates = Get-ChildItem -Path $scriptDir -Filter "*.msix" -File
}
if ($msixCandidates.Count -eq 0) {
    Fail "No se encontro ningun archivo .msix en $scriptDir"
}
if ($msixCandidates.Count -gt 1) {
    Write-Host "  Se encontraron varios .msix, se usara el mas reciente:" -ForegroundColor Yellow
    $msixCandidates | ForEach-Object { Write-Host "    - $($_.Name)" }
}
$msix = ($msixCandidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
Write-Host "  MSIX: $msix"
Write-Host "  Certificado: $cert"

# --- 2. Import certificate (idempotent) -------------------------------------
Write-Step "Instalando el certificado en LocalMachine\TrustedPeople..."
try {
    Import-Certificate -FilePath $cert -CertStoreLocation "Cert:\LocalMachine\TrustedPeople" | Out-Null
} catch {
    Fail "No se pudo importar el certificado: $($_.Exception.Message)"
}
Write-Host "  OK." -ForegroundColor Green

# --- 3. Install / update the MSIX -------------------------------------------
Write-Step "Instalando Wavora..."
try {
    Add-AppxPackage -Path $msix -ForceApplicationShutdown -ForceUpdateFromAnyVersion -ErrorAction Stop
} catch {
    Write-Host "  La actualizacion in-place fallo (posible cambio de firma/publisher)." -ForegroundColor Yellow
    Write-Host "  Intentando desinstalar la version anterior y reinstalar limpio..."
    Get-AppxPackage -Name "*Wavora*" -AllUsers -ErrorAction SilentlyContinue |
        Remove-AppxPackage -AllUsers -ErrorAction SilentlyContinue
    try {
        Add-AppxPackage -Path $msix -ForceApplicationShutdown -ErrorAction Stop
    } catch {
        Fail "Add-AppxPackage fallo: $($_.Exception.Message)`n`nSoluciones comunes:`n  - Activar 'Instalar aplicaciones de cualquier origen' en Configuracion -> Privacidad y seguridad -> Opciones para desarrolladores.`n  - Reiniciar Windows y volver a correr install-wavora.bat."
    }
}
Write-Host "  OK." -ForegroundColor Green

# --- 4. Resolve the installed app's shell AppID (never hardcode this) ------
Write-Step "Buscando la aplicacion instalada..."
Start-Sleep -Seconds 1  # give the shell a moment to register the new package
$startApp = Get-StartApps | Where-Object { $_.Name -like "*Wavora*" } | Select-Object -First 1
if (-not $startApp) {
    Write-Host "  [WARN] No se pudo resolver el AppID via Get-StartApps; se omite el acceso directo de escritorio." -ForegroundColor Yellow
} else {
    $appId = $startApp.AppID
    Write-Host "  AppID: $appId"

    # --- 5. Create Desktop shortcut ------------------------------------------
    Write-Step "Creando acceso directo en el escritorio..."
    try {
        $desktop = [Environment]::GetFolderPath("Desktop")
        $shortcutPath = Join-Path $desktop "Wavora.lnk"
        $shell = New-Object -ComObject WScript.Shell
        $shortcut = $shell.CreateShortcut($shortcutPath)
        $shortcut.TargetPath = "$env:WINDIR\explorer.exe"
        $shortcut.Arguments = "shell:AppsFolder\$appId"
        $shortcut.Description = "Wavora"
        $shortcut.WorkingDirectory = "$env:WINDIR"
        # Sin esto, Windows resuelve el icono del .lnk a partir de TargetPath
        # (explorer.exe) y muestra su icono generico de carpeta en vez del de
        # Wavora, aunque la app abra y se vea perfecta una vez corriendo.
        if ($icon) {
            $shortcut.IconLocation = "$icon,0"
        }
        $shortcut.Save()
        Write-Host "  OK: $shortcutPath" -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] No se pudo crear el acceso directo: $($_.Exception.Message)" -ForegroundColor Yellow
    }

    # --- 6. Launch the app ----------------------------------------------------
    Write-Step "Abriendo Wavora..."
    try {
        Start-Process "shell:AppsFolder\$appId"
    } catch {
        Write-Host "  [WARN] No se pudo abrir la app automaticamente. Buscala en el menu de inicio." -ForegroundColor Yellow
    }
}

# --- 7. Entrada de desinstalacion clasica: DESACTIVADA A PROPOSITO -----------
# AUDIT NOTE (reversión, ronda posterior de la misma auditoría): esto SÍ
# generaba una segunda entrada real "Wavora" en Configuración ->
# Aplicaciones/Programas y características (una la del paquete MSIX
# nativo, gestionada por Windows; otra esta, con UninstallString apuntando
# a uninstall-wavora.bat) - documentado como comportamiento esperado en
# la nota anterior, pero confirmado que genera confusión real en el
# usuario ("aparece 2 veces, una sin peso y otra de 366mb").
#
# Se decide priorizar una sola entrada visible. Costo conocido y aceptado:
# sin esta clave, desinstalar el paquete MSIX (desde Configuración, con su
# única entrada nativa restante) ya NO dispara la limpieza extra que hacía
# uninstall-wavora.bat (el acceso directo de escritorio en
# %LOCALAPPDATA%\Wavora y su copia de ícono pueden quedar huérfanos). Es
# un costo cosmético menor (un .lnk viejo) frente a confundir al usuario
# con dos entradas - los scripts persistentes de desinstalación
# (uninstall.ps1 / uninstall-wavora.bat) se siguen copiando más arriba en
# este archivo por si se necesita correrlos a mano, simplemente ya no se
# registran como una entrada separada de Windows.
#
# if ($uninstallScriptsOk) { ... } # bloque completo removido - ver git
# history de este archivo si hace falta restaurarlo.

Write-Host ""
Write-Host "Wavora se instalo correctamente." -ForegroundColor Green
Write-Host "Las actualizaciones futuras se aplican solas en segundo plano."
Write-Host ""
