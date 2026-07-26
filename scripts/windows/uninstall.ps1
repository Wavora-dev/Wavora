<#
.SYNOPSIS
    Wavora Windows uninstaller.

.DESCRIPTION
    Contraparte simetrica de install.ps1. Necesaria porque MSIX no tiene
    ningun gancho de desinstalacion personalizado: el paquete MSIX en si
    mismo se borra solo, pero no sabe que install.ps1 alguna vez creo un
    acceso directo en el Escritorio (Wavora.lnk) y una copia del icono en
    %LOCALAPPDATA%\Wavora\wavora.ico (ver los AUDIT NOTE de install.ps1,
    paso 4).

    Desde que install.ps1 registra una entrada clasica en
    HKLM\...\Uninstall\Wavora (ver su paso 7), este script SI se ejecuta
    automaticamente cuando el usuario hace clic en "Desinstalar" para
    Wavora en Configuracion -> Aplicaciones - no hace falta correrlo a
    mano. (La copia usada para eso vive en %LOCALAPPDATA%\Wavora\, no en
    la carpeta temporal del instalador, asi que sigue estando disponible
    aunque el usuario haya borrado esa carpeta hace rato).

    Sigue pudiendo correrse a mano tambien (via uninstall-wavora.bat, o
    directamente) para el mismo resultado.

    Steps:
      1. Elevate to admin if needed (Remove-AppxPackage -AllUsers lo pide).
      2. Remove-AppxPackage de cualquier paquete "*Wavora*" instalado.
      3. Borrar el acceso directo del escritorio (Wavora.lnk), si existe.
      4. Borrar %LOCALAPPDATA%\Wavora\wavora.ico (el icono persistente que
         install.ps1 copio ahi), si existe.
      5. Borrar la clave HKLM\...\Uninstall\Wavora que install.ps1 registro
         (si no se borra, Windows seguiria mostrando la entrada "Wavora" en
         Configuracion -> Aplicaciones aunque ya no quede nada instalado).

    NO toca %LOCALAPPDATA%\Wavora\logs ni \cache ni ninguna otra carpeta de
    datos del usuario, ni los propios uninstall.ps1/uninstall-wavora.bat
    copiados en %LOCALAPPDATA%\Wavora\ (borrar el script que se esta
    ejecutando a si mismo es propenso a fallar mientras PowerShell todavia
    lo tiene abierto - se dejan esos dos archivos, son inofensivos). Si en
    el futuro se quiere ofrecer borrar tambien los datos de la app, deberia
    ser una opcion aparte y explicita, no parte de este cleanup.

.NOTES
    Run via uninstall-wavora.bat (maneja el prompt de UAC), o directamente:
    powershell -ExecutionPolicy Bypass -File uninstall.ps1
#>

[CmdletBinding()]
param(
    [switch]$Silent
)

$ErrorActionPreference = "Stop"

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
        exit 1
    }
    Write-Host ""
    Write-Host "[ERROR] $msg" -ForegroundColor Red
    Write-Host ""
    Read-Host "Presiona Enter para cerrar"
    exit 1
}

# --- 0. Verify we're elevated -------------------------------------------------
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Fail "Este script necesita permisos de administrador (para Remove-AppxPackage -AllUsers). Ejecuta uninstall-wavora.bat en su lugar, o corre PowerShell 'Run as Administrator'."
}

# --- 1. Remove the MSIX package -----------------------------------------------
Write-Step "Desinstalando el paquete Wavora..."
$installed = Get-AppxPackage -Name "*Wavora*" -AllUsers -ErrorAction SilentlyContinue
if (-not $installed) {
    Write-Info "  No se encontro ningun paquete Wavora instalado (puede que ya se haya desinstalado desde Configuracion)."
} else {
    try {
        $installed | Remove-AppxPackage -AllUsers -ErrorAction Stop
        Write-Host "  OK." -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] No se pudo desinstalar el paquete: $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host "  Se sigue igual con la limpieza del acceso directo y el icono." -ForegroundColor Yellow
    }
}

# --- 2. Remove the Desktop shortcut created by install.ps1 --------------------
Write-Step "Eliminando el acceso directo del escritorio..."
$desktop = [Environment]::GetFolderPath("Desktop")
$shortcutPath = Join-Path $desktop "Wavora.lnk"
if (Test-Path $shortcutPath) {
    try {
        Remove-Item -Path $shortcutPath -Force
        Write-Host "  OK: $shortcutPath" -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] No se pudo borrar ${shortcutPath}: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Info "  No se encontro $shortcutPath (ya estaba borrado, o nunca se creo)."
}

# --- 3. Remove the persistent icon copy install.ps1 created -------------------
Write-Step "Eliminando el icono persistente..."
$persistentIcon = Join-Path $env:LOCALAPPDATA "Wavora\wavora.ico"
if (Test-Path $persistentIcon) {
    try {
        Remove-Item -Path $persistentIcon -Force
        Write-Host "  OK: $persistentIcon" -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] No se pudo borrar ${persistentIcon}: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Info "  No se encontro $persistentIcon (ya estaba borrado, o nunca se creo)."
}

# --- 4. Remove the registry Uninstall entry install.ps1 created ---------------
Write-Step "Eliminando la entrada de desinstalacion..."
$uninstallKey = "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Wavora"
if (Test-Path $uninstallKey) {
    try {
        Remove-Item -Path $uninstallKey -Recurse -Force
        Write-Host "  OK." -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] No se pudo borrar la clave ${uninstallKey}: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Info "  No se encontro la clave $uninstallKey (ya estaba borrada, o nunca se creo)."
}

Write-Host ""
Write-Host "Wavora se desinstalo correctamente." -ForegroundColor Green
Write-Host ""
