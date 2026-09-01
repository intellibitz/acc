#!/usr/bin/env python3
# ==============================================================================
# AI COMMAND CENTER (acc) - UNIFIED UNIVERSAL ORCHESTRATOR
# ==============================================================================

import os
import sys
import subprocess
import argparse
import json
import shutil
import platform
import time
from pathlib import Path

# --- Configuration ---
PROJECT_ROOT = Path(__file__).parent.absolute()
CONFIG_DIR = PROJECT_ROOT / "config"
LOGS_DIR = PROJECT_ROOT / "logs"
CACHE_DIR = PROJECT_ROOT / ".cache"
REGISTRY_DIR = PROJECT_ROOT / "registry"
DATA_DIR = PROJECT_ROOT / "data"

def log(msg): print(f"[\033[1;34macc\033[0m] {msg}")
def error(msg): print(f"[\033[1;31merror\033[0m] {msg}", file=sys.stderr)

def run_shell(cmd, cwd=PROJECT_ROOT, check=True, capture=False, sudo=False):
    if sudo and platform.system() != "Windows":
        cmd = f"sudo {cmd}"
    try:
        result = subprocess.run(cmd, shell=True, cwd=str(cwd), check=check, 
                                capture_output=capture, text=True)
        return result.stdout.strip() if capture else result.returncode
    except subprocess.CalledProcessError as e:
        if not capture: error(f"Command failed: {cmd}")
        raise e

def ensure_system_deps():
    log("Auditing system dependencies...")
    missing = []
    for cmd in ["java", "python3", "jq", "curl"]:
        if not shutil.which(cmd): missing.append(cmd)
    
    if missing:
        log(f"Missing tools: {missing}. Please install them manually.")
        sys.exit(1)

def tune_hardware():
    if platform.system() != "Linux":
        log(f"[SKIP] Hardware tuning not implemented for {platform.system()}.")
        return

    log("Applying System-Level Optimizations (Requires Sudo)...")
    try:
        # 1. CPU Governor
        if shutil.which("cpupower"):
            run_shell("cpupower frequency-set -g performance", sudo=True)
        
        # 2. GPU Persistence
        if shutil.which("nvidia-smi"):
            run_shell("nvidia-smi -pm 1", sudo=True)

        # 3. Kernel VM Tuning
        run_shell('echo "vm.swappiness=1" | tee /etc/sysctl.d/99-llm-hw.conf', sudo=True)
        run_shell('sysctl -p /etc/sysctl.d/99-llm-hw.conf', sudo=True)
        log("[SUCCESS] Hardware optimized.")
    except Exception as e:
        error(f"Tuning failed: {str(e)}")

def setup_env():
    ensure_system_deps()
    
    for d in [LOGS_DIR, CACHE_DIR, REGISTRY_DIR, DATA_DIR / "backups", CONFIG_DIR]:
        d.mkdir(parents=True, exist_ok=True)

    # Ensure python dependencies
    if not (PROJECT_ROOT / ".venv").exists():
        log("Creating virtual environment...")
        run_shell("python3 -m venv .venv")
        log("Installing Python dependencies...")
        run_shell("./.venv/bin/pip install -r requirements.txt huggingface-hub")

    keystore = CONFIG_DIR / "keystore.p12"
    if not keystore.exists():
        log("Generating secure local identity...")
        if shutil.which("keytool"):
            run_shell(f'keytool -genkeypair -alias acc -keyalg RSA -keysize 2048 -storetype PKCS12 '
                      f'-keystore "{keystore}" -validity 365 -storepass password -keypass password '
                      f'-dname "CN=localhost, OU=acc, O=intellibitz, L=Unknown, ST=Unknown, C=Unknown"')
        else:
            log("[SKIP] 'keytool' not found. Skipping keystore generation.")

    fleet_json = CONFIG_DIR / "fleet.json"
    if not fleet_json.exists():
        log("Initializing default fleet...")
        default_fleet = {"models": [{"provider": "ollama", "name": "phi3", "repo": "microsoft/Phi-3-mini-4k-instruct-gguf", "filePattern": "*Q4_K_M.gguf", "tier": "FAST", "quant": "Q4_K_M", "isPrivate": False}]}
        with open(fleet_json, "w") as f: json.dump(default_fleet, f, indent=4)

    log("[SUCCESS] Environment ready. Run 'acc.py optimize' if you want to tune hardware.")

def smart_start():
    log("Launching Acc Cockpit in Folder Sandbox Mode...")
    
    # Build if needed
    jar_path = PROJECT_ROOT / "gateway/build/libs/gateway-1.0.0.jar"
    if not jar_path.exists():
        log("Gateway JAR not found. Building...")
        run_shell("./gradlew :gateway:assemble")

    # Start Gateway in background
    log("Starting Gateway...")
    cmd = f"java -jar {jar_path}"
    env = os.environ.copy()
    env["ACC_ROOT"] = str(PROJECT_ROOT)
    
    # We'll use a simple pid file for tracking
    pid_file = PROJECT_ROOT / ".gateway.pid"
    with open(LOGS_DIR / "gateway.log", "w") as log_file:
        proc = subprocess.Popen(cmd.split(), cwd=str(PROJECT_ROOT), env=env, 
                               stdout=log_file, stderr=subprocess.STDOUT)
        with open(pid_file, "w") as f:
            f.write(str(proc.pid))
    
    log(f"Gateway started (PID: {proc.pid}). Logs: {LOGS_DIR / 'gateway.log'}")
    time.sleep(2) # Wait for startup
    open_dashboard()

def open_dashboard():
    import webbrowser
    webbrowser.open("http://localhost:8333")

def handle_dev_commands(args):
    if not (PROJECT_ROOT / ".git").exists():
        error("Dev commands only available in Creator/Source mode.")
        return

    import re
    if args.dev == "test":
        try:
            current_tag = run_shell("git describe --tags --abbrev=0", capture=True)
        except:
            current_tag = "v0.0.0"
        
        base_match = re.match(r'^(v\d+\.\d+\.\d+)', current_tag)
        base_version = base_match.group(1) if base_match else "v0.0.0"
        
        if "-test." in current_tag:
            suffix = int(current_tag.split(".")[-1])
            next_tag = f"{base_version}-test.{suffix + 1}"
        else:
            parts = list(map(int, base_version[1:].split('.')))
            parts[2] += 1
            next_tag = f"v{parts[0]}.{parts[1]}.{parts[2]}-test.1"
        
        log(f"Tagging Test Release: {next_tag}")
        run_shell(f'git tag -a "{next_tag}" -m "Test Release {next_tag}"')
        run_shell(f'git push origin "{next_tag}"')

    elif args.dev == "release":
        current_tag = run_shell("git describe --tags --abbrev=0", capture=True) or "v0.0.0"
        parts = list(map(int, (re.search(r'(\d+\.\d+\.\d+)', current_tag).group(1)).split('.')))
        next_version = f"v{parts[0]}.{parts[1] + 1}.0"
        
        log(f"Creating Stable Release: {next_version}")
        branch = f"release/{next_version}"
        run_shell(f"git checkout -b {branch}")
        
        notes = f"# Release {next_version}\n\n## Changes\n"
        notes += run_shell(f'git log {current_tag}..HEAD --oneline --pretty=format:"* %s"', capture=True)
        with open(PROJECT_ROOT / "RELEASE_NOTES.md", "w") as f: f.write(notes)
        
        run_shell("git add RELEASE_NOTES.md")
        run_shell(f'git commit -m "docs: release notes for {next_version}"')
        run_shell(f'git tag -a "{next_version}" -m "Stable Release {next_version}"')
        run_shell(f"git push origin {branch} --tags")

    elif args.dev == "push":
        log("Auto-staging and pushing changes...")
        run_shell("git add .")
        # Check if there are changes to commit
        status = run_shell("git status --porcelain", capture=True)
        if status:
            run_shell('git commit -m "feat: architectural refactor and project reorganization"')
            run_shell("git push")
            log("[SUCCESS] Changes pushed to GitHub.")
        else:
            log("[INFO] No changes to push.")

def main():
    parser = argparse.ArgumentParser(description="AI Command Center Orchestrator")
    parser.add_argument("command", nargs="?", default="help", help="Command to run (setup, up, stop, uninstall, dev, optimize)")
    parser.add_argument("subcommand", nargs="?", help="Subcommand or argument")
    parser.add_argument("--force", action="store_true", help="Force action (for uninstall)")
    parser.add_argument("--dev", choices=["test", "release", "push", "benchmark"], help="Dev commands")

    args = parser.parse_args()

    if args.command == "setup": setup_env()
    elif args.command == "optimize": tune_hardware()
    elif args.command == "up":
        url = "http://localhost:8333/provisioning/up"
        if args.subcommand: url += f"?model={args.subcommand}"
        try:
            run_shell(f'curl -skX POST "{url}"')
        except: error("Gateway not responding. Is Acc running?")
    elif args.command == "stop":
        log("Stopping Acc processes...")
        pid_file = PROJECT_ROOT / ".gateway.pid"
        if pid_file.exists():
            with open(pid_file, "r") as f:
                pid = int(f.read())
            try:
                os.kill(pid, 15)
                log(f"Stopped Gateway (PID: {pid})")
            except:
                pass
            pid_file.unlink()
        
        # Kill any remaining agents/bridges
        if platform.system() != "Windows":
            run_shell("pkill -f 'brain/system_bridge.py' || true")
            run_shell("pkill -f 'brain/agent_bridge.py' || true")
        log("[SUCCESS] Processes stopped.")
    elif args.command == "uninstall":
        if (PROJECT_ROOT / ".git").exists():
            error("Uninstall blocked: Creator directory detected.")
            sys.exit(1)
        if not args.force:
            confirm = input("[PROMPT] This will delete all Acc data (models, configs). Are you sure? (y/N): ")
            if confirm.lower() != 'y': sys.exit(0)
        
        log("Stopping Acc processes...")
        # (Implicitly calls stop logic or just kills everything)
        
        log("Reclaiming disk space...")
        # SAFE DELETE: Only delete known subdirectories
        for d in [CONFIG_DIR, LOGS_DIR, CACHE_DIR, REGISTRY_DIR, DATA_DIR]:
            if d.exists(): shutil.rmtree(d)
        
        log("Acc data removed. You can now delete the 'acc.py' file manually.")
    elif args.command == "dev":
        handle_dev_commands(args)
    elif args.command in ["help", None]:
        smart_start()
    else:
        log(f"Unknown command '{args.command}'.")

if __name__ == "__main__":
    main()
