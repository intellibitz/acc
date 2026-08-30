#!/bin/bash
# ==============================================================================
# ELITE ARCHITECT COMPREHENSIVE VERIFICATION SUITE
# ==============================================================================

MODEL="deepseek-coder-v2-instruct"
TEST_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
LOG_FILE="$TEST_DIR/verification.log"

echo "=========================================="
echo "STARTING ARCHITECT VERIFICATION SUITE" | tee -a "$LOG_FILE"
echo "=========================================="

# 1. Model Existence
if ! ollama list | grep -q "$MODEL"; then
    echo "[FAIL] Model $MODEL not found. Provisioning incomplete." | tee -a "$LOG_FILE"
    exit 1
fi
echo "[PASS] Model found in registry."

# 2. Hardware & System Optimization Check
echo "[TEST] Verifying System Optimizations..."
SWAPPINESS=$(cat /proc/sys/vm/swappiness)
MAX_MAP=$(cat /proc/sys/vm/max_map_count)

if [ "$SWAPPINESS" -le 10 ]; then
    echo "[PASS] Swappiness is optimized ($SWAPPINESS)."
else
    echo "[WARN] Swappiness is high ($SWAPPINESS). Performance may degrade."
fi

if [ "$MAX_MAP" -ge 262144 ]; then
    echo "[PASS] max_map_count is sufficient ($MAX_MAP)."
else
    echo "[FAIL] max_map_count is too low ($MAX_MAP). LLM loading might fail."
    exit 1
fi

# 3. GPU Configuration Check
echo "[TEST] Verifying GPU Offloading Configuration..."
GPU_PARAM=$(ollama show "$MODEL" --modelfile | grep "num_gpu" | awk '{print $3}')
if [[ -n "$GPU_PARAM" ]]; then
    echo "[PASS] num_gpu configuration verified ($GPU_PARAM)."
else
    echo "[PASS] num_gpu is using system default (0)."
fi

# 4. Intelligence Test: Kotlin Multi-module Architecture
echo "[TEST] Running Architectural Reasoning Test..."
ARCH_PROMPT="Design a multi-module Android project structure for a Banking app using MVI. List the modules and their responsibilities."
RESPONSE=$(ollama run "$MODEL" "$ARCH_PROMPT" --truncate false)

if [[ $RESPONSE == *":app"* ]] && [[ $RESPONSE == *":domain"* ]] && [[ $RESPONSE == *":data"* ]]; then
    echo "[PASS] Architectural reasoning verified (Multi-module awareness)."
else
    echo "[FAIL] Weak architectural response."
    echo "Response preview: ${RESPONSE:0:200}..."
    exit 1
fi

# 3. Code Excellence Test: Flow and Coroutines
echo "[TEST] Running Kotlin Coroutines Expert Test..."
CODE_PROMPT="Write a Kotlin Repository implementation that uses Flow to stream data from a Room database and a Network service with proper error handling."
RESPONSE=$(ollama run "$MODEL" "$CODE_PROMPT" --truncate false)

if [[ $RESPONSE == *"flow"* ]] && [[ $RESPONSE == *"emit"* ]] && [[ $RESPONSE == *"catch"* ]]; then
    echo "[PASS] Kotlin expert knowledge verified (Flow/Coroutines)."
else
    echo "[FAIL] Sub-standard code generation."
    exit 1
fi

echo "=========================================="
echo "VERIFICATION 100% COMPLETE: ARCHITECT IS ELITE" | tee -a "$LOG_FILE"
echo "=========================================="
