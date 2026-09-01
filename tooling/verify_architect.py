#!/usr/bin/env python3
# ==============================================================================
# ELITE ARCHITECT COMPREHENSIVE VERIFICATION SUITE (Python Edition)
# ==============================================================================

import subprocess
import sys
import platform
import shutil
import time
from pathlib import Path

MODEL = "deepseek-coder-v2-instruct"
TOOLING_DIR = Path(__file__).parent.absolute()
LOG_FILE = TOOLING_DIR / "verification.log"

def log(msg, to_file=True):
    formatted = f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {msg}"
    print(msg)
    if to_file:
        with open(LOG_FILE, "a") as f:
            f.write(formatted + "\n")

def run_cmd(cmd_list, capture=True):
    try:
        result = subprocess.run(cmd_list, capture_output=capture, text=True, check=True)
        return result.stdout.strip() if capture else ""
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None

def check_model():
    log(f"[TEST] Verifying model '{MODEL}' in registry...")
    output = run_cmd(["ollama", "list"])
    if output and MODEL in output:
        log("[PASS] Model found.")
        return True
    log(f"[FAIL] Model {MODEL} not found. Provisioning incomplete.")
    return False

def check_system():
    if platform.system() != "Linux":
        log(f"[SKIP] System optimization check not applicable for {platform.system()}.")
        return True

    log("[TEST] Verifying Linux System Optimizations...")
    success = True
    
    try:
        with open("/proc/sys/vm/swappiness", "r") as f:
            swappiness = int(f.read().strip())
        if swappiness <= 10:
            log(f"[PASS] Swappiness is optimized ({swappiness}).")
        else:
            log(f"[WARN] Swappiness is high ({swappiness}). Performance may degrade.")
    except Exception as e:
        log(f"[ERROR] Could not read swappiness: {e}")

    try:
        with open("/proc/sys/vm/max_map_count", "r") as f:
            max_map = int(f.read().strip())
        if max_map >= 262144:
            log(f"[PASS] max_map_count is sufficient ({max_map}).")
        else:
            log(f"[FAIL] max_map_count is too low ({max_map}). LLM loading might fail.")
            success = False
    except Exception as e:
        log(f"[ERROR] Could not read max_map_count: {e}")
        success = False
        
    return success

def check_gpu():
    log("[TEST] Verifying GPU Offloading Configuration...")
    output = run_cmd(["ollama", "show", MODEL, "--modelfile"])
    if output:
        import re
        match = re.search(r"num_gpu\s+(\d+)", output)
        if match:
            log(f"[PASS] num_gpu configuration verified ({match.group(1)}).")
        else:
            log("[PASS] num_gpu is using system default (0 or auto).")
        return True
    log("[FAIL] Could not retrieve Modelfile.")
    return False

def run_intelligence_test(name, prompt, keywords):
    log(f"[TEST] Running Intelligence Test: {name}...")
    response = run_cmd(["ollama", "run", MODEL, prompt])
    
    if not response:
        log("[FAIL] No response from model.")
        return False
        
    response_lower = response.lower()
    missing = [k for k in keywords if k.lower() not in response_lower]
    
    if not missing:
        log(f"[PASS] {name} verified.")
        return True
    else:
        log(f"[FAIL] Weak response for {name}. Missing keywords: {missing}")
        log(f"Response preview: {response[:200]}...")
        return False

def main():
    log("==========================================")
    log("STARTING ARCHITECT VERIFICATION SUITE")
    log("==========================================")

    if not shutil.which("ollama"):
        log("[FAIL] 'ollama' CLI not found. Please install Ollama.")
        sys.exit(1)

    steps = [
        check_model,
        check_system,
        check_gpu,
        lambda: run_intelligence_test(
            "Architectural Reasoning",
            "Design a multi-module Android project structure for a Banking app using MVI. List the modules and their responsibilities.",
            [":app", ":domain", ":data"]
        ),
        lambda: run_intelligence_test(
            "Kotlin Coroutines Expert",
            "Write a Kotlin Repository implementation that uses Flow to stream data from a Room database and a Network service with proper error handling.",
            ["flow", "emit", "catch"]
        )
    ]

    for step in steps:
        if not step():
            log("==========================================")
            log("VERIFICATION FAILED")
            log("==========================================")
            sys.exit(1)

    log("==========================================")
    log("VERIFICATION 100% COMPLETE: ARCHITECT IS ELITE")
    log("==========================================")

if __name__ == "__main__":
    main()
