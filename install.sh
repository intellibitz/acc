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

mkdir -p "$BIN_DIR"

if [ -d "$INSTALL_DIR" ]; then
    log "Existing installation detected at $INSTALL_DIR"
    echo "----------------------------------------------------"
    echo "1) Update (Keep models and settings)"
    echo "2) Clean Reinstall (Fresh start - deletes everything)"
    echo "----------------------------------------------------"
    read -p "[PROMPT] Select option [1/2] (Default: 1): " -n 1 -r </dev/tty
    echo
    REPLY=${REPLY:-1}
    if [[ $REPLY == "2" ]]; then
        log "Performing clean-up before installation..."
        if [ -x "$INSTALL_DIR/acc" ]; then
            "$INSTALL_DIR/acc" uninstall --force || rm -rf "$INSTALL_DIR"
        else
            rm -rf "$INSTALL_DIR"
        fi
        log "Cleanup complete."
    else
        log "Proceeding with update..."
        # Purge potentially stale internal scripts to ensure engine-neutrality
        rm -rf "$INSTALL_DIR/common" 2>/dev/null
    fi
fi

mkdir -p "$INSTALL_DIR"
log "Downloading latest orchestrator..."
curl -sSL "https://raw.githubusercontent.com/$REPO/main/acc" -o "$INSTALL_DIR/acc"
curl -sSL "https://raw.githubusercontent.com/$REPO/main/acc.py" -o "$INSTALL_DIR/acc.py"
chmod +x "$INSTALL_DIR/acc"

# Link to bin
ln -sf "$INSTALL_DIR/acc" "$BIN_DIR/acc"

log "Initializing environment..."
cd "$INSTALL_DIR"
./acc setup

log "Fetching pre-built assets..."
curl -sSL "https://raw.githubusercontent.com/$REPO/main/docker-compose.yml" -o "$INSTALL_DIR/docker-compose.yml"

log "\033[1;32mSUCCESS!\033[0m AI Command Center is installed."
log "Type 'acc' to start your cockpit."
log "Note: Acc prioritizes Docker for a Zero-Footprint experience."

# Check if BIN_DIR is in PATH
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    log "\033[1;33mNOTE\033[0m: Please add $BIN_DIR to your PATH (e.g., in ~/.bashrc)"
fi
