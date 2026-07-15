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
      5. Launch the app.

    Bundle requirement (same folder as this script):
      - wavora.crt
      - wavora.ico   (used only for the desktop shortcut's icon)
      - a single *.msix (any name containing "wavora", e.g. wavora-1.1.2.x64.msix)

.NOTES
    Run via install-wavora.bat (handles the UAC elevation prompt), or
    directly: powershell -ExecutionPolicy Bypass -File install.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

function Write-Step($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Fail($msg) {
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
if (-not (Test-Path $icon)) {
    Write-Host "  [WARN] No se encontro wavora.ico en $scriptDir - el acceso directo de escritorio quedara sin icono propio." -ForegroundColor Yellow
    $icon = $null
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

Write-Host ""
Write-Host "Wavora se instalo correctamente." -ForegroundColor Green
Write-Host "Las actualizaciones futuras se aplican solas en segundo plano."
Write-Host ""
