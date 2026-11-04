@echo off
REM Musikku offline installer for Windows.
REM Double-click this file. UAC will prompt for admin (needed for cert + sideload).
REM
REM Bundle requirement (same folder as this .bat):
REM   - simpmusic.crt
REM   - simpmusic-<version>.msix     (any .msix matching simpmusic-*.msix)
REM
REM Only x64 MSIX is shipped — Windows 11 ARM64 hosts run it under Prism
REM emulation. ARM64 native build is blocked upstream by a missing
REM androidx.sqlite-bundled windows-arm64 native binary.

setlocal EnableExtensions
cd /d "%~dp0"

REM --- 1. Self-elevate to admin -----------------------------------------------
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting admin elevation...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo.
echo === Musikku offline installer ===
echo.

REM --- 2. Install cert to Trusted People (idempotent via certutil) -----------
if not exist "%~dp0simpmusic.crt" (
    echo [ERROR] simpmusic.crt not found next to install.bat
    pause
    exit /b 1
)

echo [1/3] Importing simpmusic.crt to LocalMachine\TrustedPeople ...
certutil -addstore -f "TrustedPeople" "%~dp0simpmusic.crt"
if %errorlevel% neq 0 (
    echo [ERROR] Certificate import failed.
    pause
    exit /b 1
)

REM --- 3. Find the bundled MSIX ----------------------------------------------
set "MSIX="
for %%f in ("%~dp0simpmusic-*.msix") do set "MSIX=%%~ff"

if not defined MSIX (
    echo [ERROR] No simpmusic-*.msix found in this folder.
    pause
    exit /b 1
)

REM The package this script replaces or launches is the one THIS MSIX installs:
REM the same Identity Name AND Publisher, i.e. the same package family. Never
REM match by name alone: Musikku keeps upstream's Identity Name "Simpmusic"
REM (app.fsname), and only its Publisher (CN=Musikku, from Conveyor's self-signed
REM certificate) tells it apart from an upstream SimpMusic installed beside it.
REM The identity is read from the MSIX's own AppxManifest.xml rather than
REM hard-coded, so it follows whatever certificate signed this build.
REM $mine lists the installed packages of that family; $app is the Application Id.
set "PS_IDENTITY=$ErrorActionPreference = 'Stop'; Add-Type -AssemblyName System.IO.Compression.FileSystem; $zip = [IO.Compression.ZipFile]::OpenRead($env:MSIX); try { $reader = New-Object IO.StreamReader($zip.GetEntry('AppxManifest.xml').Open()); [xml]$manifest = $reader.ReadToEnd(); $reader.Close() } finally { $zip.Dispose() }; $identity = $manifest.Package.Identity; $idName = $identity.GetAttribute('Name'); $idPublisher = $identity.GetAttribute('Publisher'); $app = @($manifest.Package.Applications.Application)[0].GetAttribute('Id'); $mine = { Get-AppxPackage -Name $idName | Where-Object { $_.Name -eq $idName -and $_.Publisher -eq $idPublisher } }"

echo.
echo [2/3] Installing/updating MSIX: "%MSIX%"
REM -ForceUpdateFromAnyVersion overwrites an existing install in place (keeps app
REM data) even when the version is identical, so no manual uninstall is needed.
REM If that still fails, fall back to removing the installed copy of this same
REM package family and doing a clean install.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "%PS_IDENTITY%; try { Add-AppxPackage -Path $env:MSIX -ForceApplicationShutdown -ForceUpdateFromAnyVersion -ErrorAction Stop; exit 0 } catch { Write-Host 'In-place update failed; removing the installed Musikku and reinstalling...'; & $mine | Remove-AppxPackage -ErrorAction SilentlyContinue; try { Add-AppxPackage -Path $env:MSIX -ForceApplicationShutdown -ErrorAction Stop; exit 0 } catch { Write-Host $_; exit 1 } }"

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Add-AppxPackage failed. Common fixes:
    echo   - Enable 'Install apps from any source, including loose files'
    echo     ^(Settings -^> Privacy ^& security -^> For developers^)
    echo   - Restart Windows and run install.bat again
    pause
    exit /b 1
)

REM --- 4. Launch app ---------------------------------------------------------
echo.
echo [3/3] Launching Musikku ...
REM Launched by the family name Windows gave the installed package, not a
REM hard-coded one: Simpmusic_ejp2bhxmz1qq6 is upstream SimpMusic's (CN=SimpMusic).
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "%PS_IDENTITY%; $pkg = & $mine | Select-Object -First 1; if (-not $pkg) { Write-Host 'Could not find the installed package; open Musikku from the Start menu.'; exit 1 }; Start-Process ('shell:AppsFolder\' + $pkg.PackageFamilyName + '!' + $app)"

echo.
echo Musikku installed successfully.
echo To uninstall: Settings -^> Apps -^> Installed apps -^> Musikku -^> Uninstall
echo.
pause
endlocal
