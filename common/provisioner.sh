#!/bin/bash
# ==============================================================================
# acc EXPERT LIFECYCLE CONTROLLER (V66 - USER-DRIVEN PROVISIONING)
# ==============================================================================

CORE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( dirname "$CORE_DIR" )"
DOWNLOAD_DIR="$PROJECT_ROOT/downloads"; OPT_DIR="$PROJECT_ROOT/optimizations"
SCRIPTS_DIR="$PROJECT_ROOT/tooling"; DATA_DIR="$PROJECT_ROOT/data"
LOG_DIR="$PROJECT_ROOT/logs"; LOG_FILE="$LOG_DIR/provisioner.log"
mkdir -p "$DOWNLOAD_DIR" "$OPT_DIR" "$LOG_DIR"

DL_METHOD="hf"

log() { local msg="[$(date +'%Y-%m-%d %H:%M:%S')] $1"; echo -e "$msg" | tee -a "$LOG_FILE"; }

FLEET_JSON="$PROJECT_ROOT/config/fleet.json"

get_fleet_entries() {
    if [ ! -f "$FLEET_JSON" ]; then echo "()"; return; fi
    # Parse fleet.json using jq into the pipe-separated format provisioner expects
    jq -r '.models[] | "\(.provider)|\(.name)|\(.repo)|\(.filePattern)|\(.tier)|\(.quant)|\(.superpower)|\(.isPrivate)"' "$FLEET_JSON"
}

OSM_CMD="bash $CORE_DIR/hwt.sh"
ARCHITECT_MANIFESTO="You are the Master Architect, an elite Android Lead Engineer."

merge_gguf() {
    local target_dir=$1; local output_file=$2
    local parts=($(ls "$target_dir"/*.gguf.part* 2>/dev/null | sort))
    if [ ${#parts[@]} -gt 0 ]; then
        [ -f "$output_file" ] && log "[RESUME] Appending..." || log "[MERGE] Reassembling..."
        local count=0; local total=${#parts[@]}
        for part in "${parts[@]}"; do
            cat "$part" >> "$output_file"; rm "$part"
            count=$((count + 1)); local percent=$((count * 100 / total))
            log "[MERGE] Progress: $percent%"
        done
    fi
}

calculate_gpu_layers() {
    local model_name=$1
    if [[ "$model_name" == *"70b"* ]]; then echo "20"; elif [[ "$model_name" == *"8x22b"* || "$model_name" == *"command-r-plus"* ]]; then echo "10"; else echo "99"; fi
}

get_model_params() {
    local model_name=$1; local param_file="$OPT_DIR/$model_name/user_params"
    [ -f "$param_file" ] && cat "$param_file" || echo "PARAMETER temperature 0.7\nPARAMETER top_p 0.9"
}

provision_model() {
    local entry=$1
    IFS='|' read -r provider target_name repo file_pattern tier target_quant superpower is_private <<< "$(echo "$entry" | tr -d '\r')"

    log "----------------------------------------------------"
    log ">>> SYNC CHECK: [$provider] $target_name"

    local model_opt_dir="$OPT_DIR/$target_name"; local local_sha_file="$model_opt_dir/last_sync_sha"
    mkdir -p "$model_opt_dir"

    # Cloud providers are always "provisioned"
    if [[ "$provider" == "openai" || "$provider" == "anthropic" || "$provider" == "gemini" ]]; then
        log "[ATTACH] Cloud engine '$provider' detected. Skipping local provisioning."
        echo "EXTERNAL" > "$local_sha_file"; return 0
    fi

    local remote_sha=$(curl -L -4 -s "https://huggingface.co/api/models/$repo" | jq -r '.sha' 2>/dev/null)
    local local_sha=$(cat "$local_sha_file" 2>/dev/null)

    # Check if already installed (Provider-specific check)
    local is_installed=false
    if [[ "$provider" == "ollama" ]]; then
        ollama list | grep -q "$target_name" && is_installed=true
    elif [[ "$provider" == "localai" ]]; then
        [ -f "$PROJECT_ROOT/models/$target_name.yaml" ] && is_installed=true
    fi

    if [[ "$remote_sha" == "$local_sha" ]] && [ "$is_installed" = true ] && [[ "$FORCE_REPROVISION" != "true" ]]; then
        log "[MATCH] $target_name is current."; return 0
    fi

    log "[UPDATE] Provisioning $target_name via $provider..."
    local model_dl_dir="$DOWNLOAD_DIR/$target_name"; mkdir -p "$model_dl_dir"

    if [[ "$DL_METHOD" == "hf" ]]; then
        export HF_HUB_ENABLE_HF_TRANSFER=1
        hf download "$repo" --include "$file_pattern" --local-dir "$model_dl_dir" --max-workers 8 || return 1
    fi

    local final_gguf="$model_dl_dir/model.gguf"
    local found_file=$(find "$model_dl_dir" -name "*.gguf" | head -n 1)
    if [[ -z "$found_file" ]]; then merge_gguf "$model_dl_dir" "$final_gguf"; found_file=$(find "$model_dl_dir" -name "*.gguf" | head -n 1); fi

    if [[ -z "$found_file" || ! -f "$found_file" ]]; then
        log "[ERROR] No model file found for $target_name after download."
        return 1
    fi

    if [[ "$provider" == "ollama" ]]; then
        provision_ollama "$target_name" "$found_file" "$model_opt_dir"
    elif [[ "$provider" == "localai" ]]; then
        provision_localai "$target_name" "$found_file"
    else
        log "[SKIP] Engine '$provider' provisioning not yet implemented via shell."
    fi

    echo "$remote_sha" > "$local_sha_file"
    rm -rf "$model_dl_dir"
}

provision_ollama() {
    local name=$1; local file=$2; local opt_dir=$3
    local gpu_layers=$(calculate_gpu_layers "$name"); local threads=$(nproc --ignore=4)
    local user_params=$(get_model_params "$name")

    printf "FROM %s\nPARAMETER num_gpu %s\nPARAMETER num_thread %s\nPARAMETER num_ctx 32768\n%s\nSYSTEM \"\"\"%s\"\"\"" \
        "$file" "$gpu_layers" "$threads" "$user_params" "$ARCHITECT_MANIFESTO" > "$opt_dir/Modelfile"

    log "[REGISTER] Creating Ollama model $name..."
    ollama rm "$name" >/dev/null 2>&1
    ollama create "$name" -f "$opt_dir/Modelfile" 2>&1 | tee -a "$LOG_FILE"
}

provision_localai() {
    local name=$1; local file=$2
    log "[REGISTER] Creating LocalAI config for $name..."
    # Implementation for generating LocalAI YAML
    echo "name: $name" > "$PROJECT_ROOT/models/$name.yaml"
    echo "parameters:" >> "$PROJECT_ROOT/models/$name.yaml"
    echo "  model: $file" >> "$PROJECT_ROOT/models/$name.yaml"
}

prune_fleet() {
    log ">>> STARTING FLEET PRUNING..."
    # 1. Remove models from Ollama that aren't in fleet.conf
    local installed=$(ollama list | awk 'NR>1 {print $1}' | cut -d: -f1)
    for inst in $installed; do
        local in_fleet=false
        for m in "${MODELS[@]}"; do
            IFS='|' read -r name rest <<< "$m"
            [[ "$name" == "$inst" ]] && in_fleet=true && break
        done
        if [ "$in_fleet" = false ]; then
            log "[PRUNE] Removing unmanaged model: $inst"
            ollama rm "$inst" >/dev/null 2>&1
        fi
    done
    # 2. Cleanup partial downloads
    rm -rf "$DOWNLOAD_DIR"/*
    log "[SUCCESS] Fleet pruned and disk space reclaimed."
}

backup_config() {
    local ts=$(date +%Y%m%d_%H%M%S)
    local b_dir="$PROJECT_ROOT/data/backups/$ts"
    mkdir -p "$b_dir"
    cp "$FLEET_CONF" "$b_dir/"
    cp -r "$OPT_DIR" "$b_dir/"
    cp "$PROJECT_ROOT/config/"*.env "$b_dir/"
    log "[SUCCESS] Configuration backed up to $b_dir"
}

auto_scale() {
    log ">>> RUNNING HARDWARE-AWARE AUTO-SCALING..."
    if command -v nvidia-smi &> /dev/null; then
        VRAM=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits | head -n 1)
    else
        VRAM=0
    fi
    RAM=$(free -g | awk '/^Mem:/{print $2}')
    log "[DETECT] VRAM: ${VRAM}MB | RAM: ${RAM}GB"

    if [ "$VRAM" -ge 40000 ]; then
        log "[TARGET] Tier: ELITE (Ready for 70B+)"
    elif [ "$VRAM" -ge 16000 ]; then
        log "[TARGET] Tier: STRONG (Ready for 30B-70B)"
    else
        log "[TARGET] Tier: FAST (Ready for <14B)"
    fi
    log "[INFO] Use 'acc add' to customize your fleet further."
}

save_fleet() {
    printf "MODELS=(\n" > "$FLEET_CONF"
    for m in "${MODELS[@]}"; do printf "    \"%s\"\n" "$m"; done >> "$FLEET_CONF"
    printf ")\n" >> "$FLEET_CONF"
    log "[SUCCESS] Fleet configuration saved."
}

save_private_fleet() {
    printf "PRIVATE_MODELS=(\n" > "$PRIVATE_FLEET_CONF"
    for m in "${PRIVATE_MODELS[@]}"; do printf "    \"%s\"\n" "$m"; done >> "$PRIVATE_FLEET_CONF"
    printf ")\n" >> "$PRIVATE_FLEET_CONF"
    log "[SUCCESS] Private fleet configuration saved."
}

remove_model() {
    local name=$1; local new_fleet=(); local found=false
    for m in "${MODELS[@]}"; do
        IFS='|' read -r m_name rest <<< "$m"
        [[ "$m_name" == "$name" ]] && found=true && continue
        new_fleet+=("$m")
    done
    if [ "$found" = true ]; then MODELS=("${new_fleet[@]}"); save_fleet; return; fi

    new_fleet=(); found=false
    for m in "${PRIVATE_MODELS[@]}"; do
        IFS='|' read -r m_name rest <<< "$m"
        [[ "$m_name" == "$name" ]] && found=true && continue
        new_fleet+=("$m")
    done
    if [ "$found" = true ]; then PRIVATE_MODELS=("${new_fleet[@]}"); save_private_fleet; return; fi
    log "[ERROR] Model '$name' not found in any fleet."
}

tune_model() {
    local name=$1; local params=$2
    local model_opt_dir="$OPT_DIR/$name"
    mkdir -p "$model_opt_dir"
    echo -e "$params" > "$model_opt_dir/user_params"
    log "[SUCCESS] Parameters updated for $name. Please run 'acc up' to apply."
}

provision_fleet() {
    local filter=$1
    get_fleet_entries | while read -r entry; do
        [ -z "$entry" ] && continue
        IFS='|' read -r provider name rest <<< "$(echo "$entry" | tr -d '\r')"
        if [[ -n "$filter" ]]; then
            if [[ "$name" != "$filter" ]]; then continue; fi
        fi
        provision_model "$entry"
    done
}

# Parse args
FILTER_NAME=""
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --method) DL_METHOD="$2"; shift ;;
        --private) IS_PRIVATE="true" ;;
        maintain|provision|fitness|prune|backup|auto-scale)
            CMD="$1"
            if [[ -n "$2" && "$2" != --* ]]; then
                FILTER_NAME="$2"
                shift
            fi
            ;;
        search) CMD="search"; QUERY="$2"; shift ;;
        add) CMD="add"; ENTRY="$2"; shift ;;
        remove) CMD="remove"; NAME="$2"; shift ;;
        tune-model) CMD="tune-model"; T_NAME="$2"; T_PARAMS="$3"; shift 2 ;;
    esac
    shift
done

case "$CMD" in
    maintain) provision_fleet "$FILTER_NAME" ;;
    search) search_hf "$QUERY" ;;
    add)
        if [ "$IS_PRIVATE" == "true" ]; then
            PRIVATE_MODELS+=("$ENTRY"); save_private_fleet
        else
            MODELS+=("$ENTRY"); save_fleet
        fi
        ;;
    remove) remove_model "$NAME" ;;
    tune-model) tune_model "$T_NAME" "$T_PARAMS" ;;
    prune) prune_fleet ;;
    backup) backup_config ;;
    auto-scale) auto_scale ;;
    *) echo "Usage: provisioner {maintain [model]|search|add|remove|tune-model|prune|backup|auto-scale}"; exit 1 ;;
esac
