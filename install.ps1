# ==============================================================================
# acc ONE-LINER INSTALLER (Windows/PowerShell)
# ==============================================================================

$INSTALL_DIR = Join-Path $HOME ".acc"
$BIN_DIR = Join-Path $HOME "AppData\Local\Microsoft\WindowsApps" # Common user bin dir
$REPO = "intellibitz/acc"

function Write-Log($msg) { Write-Host "[$([char]27)[1;34macc-install$([char]27)[0m] $msg" }

if (!(Test-Path $INSTALL_DIR)) { New-Item -ItemType Directory -Path $INSTALL_DIR -Force | Out-Null }

Write-Log "Downloading latest orchestrator..."
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/$REPO/main/acc.ps1" -OutFile (Join-Path $INSTALL_DIR "acc.ps1")
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/$REPO/main/acc.py" -OutFile (Join-Path $INSTALL_DIR "acc.py")

Write-Log "Initializing environment..."
Set-Location $INSTALL_DIR
& .\acc.ps1 setup

Write-Log "Fetching pre-built assets..."
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/$REPO/main/docker-compose.yml" -OutFile (Join-Path $INSTALL_DIR "docker-compose.yml")

# Create a shim or alias for 'acc'
$shimContent = @"
@echo off
powershell -ExecutionPolicy Bypass -File "$INSTALL_DIR\acc.ps1" %*
"@
$shimContent | Out-File -FilePath (Join-Path $BIN_DIR "acc.bat") -Encoding ascii

Write-Log "`n$([char]27)[1;32mSUCCESS!$([char]27)[0m AI Command Center is installed."
Write-Log "Type 'acc' to start your cockpit."
Write-Log "Note: Acc prioritizes Docker for a Zero-Footprint experience."
