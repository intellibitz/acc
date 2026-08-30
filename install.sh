#!/bin/bash
# ==============================================================================
# AI COMMAND CENTER (ACC) - ZERO-EFFORT INSTALLER
# ==============================================================================

set -e

PROJECT_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BIN_TARGET="/usr/local/bin/acc"

log() { echo -e "[\033[1;32mINSTALLER\033[0m] $1"; }
error() { echo -e "[\033[1;31mERROR\033[0m] $1"; exit 1; }

log "Setting up AI Command Center (ACC)..."

# 1. Check for basic requirements
for cmd in tmux curl jq pip3; do
    command -v $cmd >/dev/null 2>&1 || error "$cmd is not installed. Please install it first."
done

# 2. Make script executable
chmod +x "$PROJECT_ROOT/acc"
chmod +x "$PROJECT_ROOT/Core/"*.sh

# 3. Create symlink
if [ -L "$BIN_TARGET" ]; then
    sudo rm "$BIN_TARGET"
fi

log "Linking acc to $BIN_TARGET..."
sudo ln -s "$PROJECT_ROOT/acc" "$BIN_TARGET"

# 4. Install python dependencies
log "Installing Python dependencies..."
pip3 install -r "$PROJECT_ROOT/requirements.txt" --quiet

log "\n\033[1;36mSUCCESS!\033[0m"
log "You can now launch the command center by simply typing: \033[1;37macc\033[0m"
log "Try it now: \033[1;33macc dash\033[0m"
