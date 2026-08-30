#!/bin/bash
# ==============================================================================
# ACC SERVICE MANAGER & HARDWARE TUNER (V6 - ULTIMATE)
# ==============================================================================

CORE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( dirname "$CORE_DIR" )"
CONF_DIR="/etc/systemd/system/ollama.service.d"
CONF_FILE="$CONF_DIR/optimize.conf"
ENV_FILE="$PROJECT_ROOT/Config/ollama.env"
HW_FILE="$PROJECT_ROOT/Config/hardware.env"

detect_hardware() {
    CPU_CORES=$(nproc); THREADS=$((CPU_CORES > 16 ? CPU_CORES - 4 : CPU_CORES - 2))
    [ "$THREADS" -lt 1 ] && THREADS=1
    TOTAL_RAM_GB=$(free -g | awk '/^Mem:/{print $2}')
    if command -v nvidia-smi &> /dev/null; then
        GPU_VRAM=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits | head -n 1)
        HAS_GPU=true
    else GPU_VRAM=0; HAS_GPU=false; fi
}

generate_config() {
    detect_hardware
    [ -f "$ENV_FILE" ] && source "$ENV_FILE"
    : ${OLLAMA_KEEP_ALIVE:="24h"}; : ${OLLAMA_MAX_LOADED_MODELS:="1"}
    : ${OLLAMA_NUM_PARALLEL:="1"}; : ${OLLAMA_NUM_THREADS:=$THREADS}
    : ${OLLAMA_KV_CACHE_TYPE:="q4_0"}; : ${OLLAMA_FLASH_ATTENTION:="1"}
    : ${OLLAMA_CONTEXT_LENGTH:="32768"}; : ${OLLAMA_GPU_OVERHEAD:="1024"}
    : ${OLLAMA_HOST:="0.0.0.0:11434"}

    cat <<EOF
[Service]
Environment="OLLAMA_KEEP_ALIVE=$OLLAMA_KEEP_ALIVE"
Environment="OLLAMA_MAX_LOADED_MODELS=$OLLAMA_MAX_LOADED_MODELS"
Environment="OLLAMA_NUM_PARALLEL=$OLLAMA_NUM_PARALLEL"
Environment="OLLAMA_NUM_THREADS=$OLLAMA_NUM_THREADS"
Environment="OLLAMA_KV_CACHE_TYPE=$OLLAMA_KV_CACHE_TYPE"
Environment="OLLAMA_FLASH_ATTENTION=$OLLAMA_FLASH_ATTENTION"
Environment="OLLAMA_USE_MMAP=1"
Environment="OLLAMA_CONTEXT_LENGTH=$OLLAMA_CONTEXT_LENGTH"
Environment="OLLAMA_MAX_QUEUE=1"
Environment="OLLAMA_GPU_OVERHEAD=$OLLAMA_GPU_OVERHEAD"
$( [ "$HAS_GPU" = true ] && echo "Environment=\"OLLAMA_SCHED_SPREAD=1\"" )
Environment="OLLAMA_HOST=$OLLAMA_HOST"
EOF
}

sync_config() {
    echo ">>> Synchronizing Performance Configuration..."
    local DYNAMIC_CONFIG=$(generate_config)
    [ ! -d "$CONF_DIR" ] && sudo mkdir -p "$CONF_DIR"
    echo "$DYNAMIC_CONFIG" | sudo tee "$CONF_FILE" > /dev/null
    sudo systemctl daemon-reload; sudo systemctl restart ollama
    echo "[SUCCESS] Service synchronized and restarted."
}

tune_hardware() {
    echo ">>> Applying Hardware Optimizations..."
    [ -f "$HW_FILE" ] && source "$HW_FILE"
    if command -v cpupower &> /dev/null; then sudo cpupower frequency-set -g ${CPU_GOVERNOR:-performance} > /dev/null; fi
    if [ "$HAS_GPU" = true ]; then
        sudo nvidia-smi -pm ${GPU_PERSISTENCE:-1} > /dev/null
        [[ "$GPU_POWER_LIMIT" != "default" ]] && sudo nvidia-smi -pl $GPU_POWER_LIMIT > /dev/null
    fi
    echo "vm.swappiness=${SYS_SWAPPINESS:-1}" | sudo tee /etc/sysctl.d/99-llm-hw.conf > /dev/null
    echo "vm.vfs_cache_pressure=${SYS_VFS_CACHE_PRESSURE:-50}" | sudo tee -a /etc/sysctl.d/99-llm-hw.conf > /dev/null
    sudo sysctl -p /etc/sysctl.d/99-llm-hw.conf > /dev/null
    [ -f /sys/kernel/mm/transparent_hugepage/enabled ] && echo ${SYS_THP:-always} | sudo tee /sys/kernel/mm/transparent_hugepage/enabled > /dev/null
    echo "[SUCCESS] Hardware tuned."
}

update_ollama() {
    echo ">>> Checking for Ollama updates..."
    curl -fsSL https://ollama.com/install.sh | sh
    echo "[SUCCESS] Ollama update sequence completed."
}

rotate_logs() {
    echo ">>> Cleaning and rotating logs..."
    local log_file="$PROJECT_ROOT/Logs/provisioner.log"
    if [ -f "$log_file" ]; then
        mv "$log_file" "$log_file.$(date +%Y%m%d)"
        touch "$log_file"
        echo "[SUCCESS] Logs rotated."
    fi
}

case "$1" in
    start) sudo systemctl start ollama ;;
    stop) sudo systemctl stop ollama ;;
    restart) sudo systemctl daemon-reload; sudo systemctl restart ollama ;;
    sync) sync_config ;;
    tune) tune_hardware ;;
    update-engine) update_ollama ;;
    rotate) rotate_logs ;;
    status|*) systemctl is-active --quiet ollama && echo "ACTIVE" || echo "INACTIVE" ;;
esac
