#!/bin/bash
# ==============================================================================
# acc UNINSTALLER - TOTAL SYSTEM RESTORATION
# ==============================================================================

set -e

INSTALL_DIR="$HOME/.acc"
BIN_DIR="$HOME/.local/bin"

log() { echo -e "[\033[1;34macc-uninstall\033[0m] $1"; }
warn() { echo -e "[\033[1;33mwarning\033[0m] $1"; }

if [ ! -d "$INSTALL_DIR" ]; then
    log "Acc is not installed in $INSTALL_DIR. Nothing to do."
    exit 0
fi

read -p "[PROMPT] This will delete all models and configurations. Are you sure? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log "Uninstall cancelled."
    exit 0
fi

log "Stopping infrastructure..."
if command -v docker &> /dev/null && [ -f "$INSTALL_DIR/docker-compose.yml" ]; then
    cd "$INSTALL_DIR" && docker compose down -v || warn "Docker containers already stopped or missing."
fi

# Stop any host-side processes
pkill -f "cc.thevar.acc" || true

log "Reclaiming disk space..."
rm -rf "$INSTALL_DIR"

log "Removing global binary link..."
rm -f "$BIN_DIR/acc"

# Cleanup hardware tweaks if they exist
if [ -f "/etc/sysctl.d/99-llm-hw.conf" ]; then
    log "Detected hardware tweaks. Removing..."
    sudo rm "/etc/sysctl.d/99-llm-hw.conf" && sudo sysctl -p /etc/sysctl.d/99-llm-hw.conf || warn "Could not remove kernel tweaks automatically."
fi

log "\033[1;32mSUCCESS!\033[0m Acc has been completely removed. Your system is as it was."
