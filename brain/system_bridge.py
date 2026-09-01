import subprocess
import json
import os
import requests
import re
import time
import sys

# Robust import handling for "Zero-Effort" diagnostics
try:
    import psutil
except ImportError:
    psutil = None

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def get_gpu_stats():
    # NVIDIA Support
    try:
        cmd = ["nvidia-smi", "--query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu,power.draw", "--format=csv,noheader,nounits"]
        output = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode().strip()
        parts = output.split(", ")
        return {
            "utilization": float(parts[0]), "memoryUsed": float(parts[1]), "memoryTotal": float(parts[2]),
            "temperature": float(parts[3]), "power": float(parts[4]), "active": True, "type": "NVIDIA"
        }
    except: pass

    # macOS / Apple Silicon Support (Basic)
    if sys.platform == "darwin":
        try:
            # Simple check for MPS availability via a small shell hack or logic
            return {"active": True, "type": "APPLE_SILICON", "utilization": 0} # Placeholder for specialized MPS stats
        except: pass

    return {"utilization": 0, "memoryUsed": 0, "memoryTotal": 0, "temperature": 0, "power": 0, "active": False, "type": "CPU"}

def get_fleet_disk_usage():
    try:
        # Platform-aware path detection
        if sys.platform == "darwin":
            ollama_dir = os.path.expanduser("~/Library/Application Support/Ollama/models")
        else:
            # Sandbox mode: check local data folder first, fallback to user home
            sandbox_dir = os.path.join(PROJECT_ROOT, "data/ollama/models")
            if os.path.exists(sandbox_dir):
                ollama_dir = sandbox_dir
            else:
                ollama_dir = os.path.expanduser("~/.ollama/models")

        if not os.path.exists(ollama_dir): return "0B"
        process = subprocess.Popen(["du", "-sh", ollama_dir], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, _ = process.communicate()
        return out.decode().split()[0]
    except: return "ERR"

def get_ollama_status():
    try:
        # Check OLLAMA_HOST from env or default
        base_url = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
        resp = requests.get(f"{base_url}/api/tags", timeout=1)
        if resp.status_code == 200:
            models = resp.json().get("models", [])
            ps_resp = requests.get(f"{base_url}/api/ps", timeout=1)
            running = ps_resp.json().get("models", []) if ps_resp.status_code == 200 else []
            return {"online": True, "models": models, "running": running}
        return {"online": False}
    except: return {"online": False}

def get_fleet_config():
    fleet_json_path = os.path.join(PROJECT_ROOT, "config/fleet.json")
    managed = []
    
    if os.path.exists(fleet_json_path):
        try:
            with open(fleet_json_path, 'r') as f:
                data = json.load(f)
                for m in data.get("models", []):
                    managed.append({
                        "name": m["name"],
                        "provider": m.get("provider", "ollama"),
                        "isPrivate": m.get("isPrivate", False)
                    })
        except: pass
    return managed

def get_system_state():
    if psutil is None:
        return {"error": "Missing dependency: psutil. Run './acc setup' to fix.", "stats": None}

    cpu = psutil.cpu_percent()
    ram = psutil.virtual_memory()
    gpu = get_gpu_stats()
    disk = get_fleet_disk_usage()
    
    ollama = get_ollama_status()
    managed_models = get_fleet_config()
    
    fleet = []
    installed_ollama_names = [m["name"].split(":")[0] for m in ollama.get("models", [])] if ollama.get("online") else []
    running_ollama_names = [r["name"].split(":")[0] for r in ollama.get("running", [])] if ollama.get("online") else []

    # Map managed models first
    for m in managed_models:
        is_installed = False
        is_running = False
        
        if m["provider"] == "ollama":
            is_installed = m["name"] in installed_ollama_names
            is_running = m["name"] in running_ollama_names
        elif m["provider"] in ["openai", "anthropic", "gemini"]:
            is_installed = True # Cloud is always "installed"
            is_running = True # Assume online for now
            
        fleet.append({
            "name": m["name"],
            "provider": m["provider"],
            "isInstalled": is_installed,
            "isRunning": is_running,
            "type": "PRIV" if m["isPrivate"] else "COMM"
        })

    # Add any rogue ollama models not in fleet.json
    for inst in installed_ollama_names:
        if not any(f["name"] == inst for f in fleet):
            fleet.append({
                "name": inst,
                "provider": "ollama",
                "isInstalled": True,
                "isRunning": inst in running_ollama_names,
                "type": "COMM"
            })

    return {
        "stats": {
            "cpuUtilization": cpu,
            "ramUsed": ram.used / (1024**3),
            "ramTotal": ram.total / (1024**3),
            "gpu": gpu,
            "diskUsage": disk
        },
        "fleet": fleet,
        "proxyOnline": False,
        "engineOnline": ollama.get("online", False)
    }

if __name__ == "__main__":
    while True:
        try:
            state = get_system_state()
            print(json.dumps(state))
            sys.stdout.flush()
        except Exception as e:
            sys.stderr.write(f"Bridge Error: {str(e)}\n")
            sys.stderr.flush()
        time.sleep(2)
