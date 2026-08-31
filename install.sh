#!/bin/bash
# ==============================================================================
# acc ONE-LINER INSTALLER
# ==============================================================================

set -e

INSTALL_DIR="$HOME/.acc"
BIN_DIR="$HOME/.local/bin"
REPO="intellibitz/acc"

log() { echo -e "[\033[1;34macc-install\033[0m] $1"; }
error() { echo -e "[\033[1;31merror\033[0m] $1"; exit 1; }

mkdir -p "$INSTALL_DIR" "$BIN_DIR"

log "Downloading latest orchestrator..."
curl -sSL "https://raw.githubusercontent.com/$REPO/main/acc" -o "$INSTALL_DIR/acc"
chmod +x "$INSTALL_DIR/acc"

# Link to bin
ln -sf "$INSTALL_DIR/acc" "$BIN_DIR/acc"

log "Initializing environment..."
cd "$INSTALL_DIR"
./acc setup

log "Fetching pre-built cockpit..."
# In a real release, this would download the Shadow JAR from GitHub Releases
# For now, we'll guide the user to the local build if they have the source
# but the vision is: curl -L https://github.com/$REPO/releases/latest/download/acc-gateway.jar -o bin/gateway.jar

log "\033[1;32mSUCCESS!\033[0m AI Command Center is installed."
log "Type 'acc' to start your cockpit."

# Check if BIN_DIR is in PATH
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    log "\033[1;33mNOTE\033[0m: Please add $BIN_DIR to your PATH (e.g., in ~/.bashrc)"
fi
