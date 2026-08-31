#!/bin/bash
# ==============================================================================
# acc HARDWARE TUNER (HWT) - ENGINE NEUTRAL OPTIMIZER
# ==============================================================================

CORE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( dirname "$CORE_DIR" )"
HW_FILE="$PROJECT_ROOT/config/hardware.env"

log() { echo -e "[\033[1;35mhwt\033[0m] $1"; }

detect_hardware() {
    CPU_CORES=$(nproc); THREADS=$((CPU_CORES > 16 ? CPU_CORES - 4 : CPU_CORES - 2))
    [ "$THREADS" -lt 1 ] && THREADS=1
    TOTAL_RAM_GB=$(free -g | awk '/^Mem:/{print $2}')
    if command -v nvidia-smi &> /dev/null; then
        GPU_VRAM=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits | head -n 1)
        HAS_GPU=true
    else GPU_VRAM=0; HAS_GPU=false; fi
}

tune_hardware() {
    log "Applying System-Level Optimizations..."
    [ -f "$HW_FILE" ] && source "$HW_FILE"

    # 1. CPU Governor
    if command -v cpupower &> /dev/null; then
        sudo cpupower frequency-set -g ${CPU_GOVERNOR:-performance} > /dev/null
        log "CPU Governor set to ${CPU_GOVERNOR:-performance}"
    fi

    # 2. GPU Persistence & Power Limits
    if [ "$HAS_GPU" = true ]; then
        sudo nvidia-smi -pm ${GPU_PERSISTENCE:-1} > /dev/null
        [[ "$GPU_POWER_LIMIT" != "default" ]] && sudo nvidia-smi -pl $GPU_POWER_LIMIT > /dev/null
        log "NVIDIA Optimizations applied (Persistence: ${GPU_PERSISTENCE:-1})"
    fi

    # 3. Kernel Virtual Memory Tuning
    echo "vm.swappiness=${SYS_SWAPPINESS:-1}" | sudo tee /etc/sysctl.d/99-llm-hw.conf > /dev/null
    echo "vm.vfs_cache_pressure=${SYS_VFS_CACHE_PRESSURE:-50}" | sudo tee -a /etc/sysctl.d/99-llm-hw.conf > /dev/null
    sudo sysctl -p /etc/sysctl.d/99-llm-hw.conf > /dev/null

    # 4. Transparent Huge Pages
    [ -f /sys/kernel/mm/transparent_hugepage/enabled ] && echo ${SYS_THP:-always} | sudo tee /sys/kernel/mm/transparent_hugepage/enabled > /dev/null

    log "[SUCCESS] Hardware tuned for AI workloads."
}

case "$1" in
    tune) detect_hardware; tune_hardware ;;
    status)
        detect_hardware
        echo "CPU Threads: $THREADS | RAM: ${TOTAL_RAM_GB}GB | GPU: ${HAS_GPU:-false} (${GPU_VRAM}MB)"
        ;;
    *) echo "Usage: hwt {tune|status}" ;;
esac
