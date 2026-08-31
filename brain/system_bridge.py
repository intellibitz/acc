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
    try:
        cmd = ["nvidia-smi", "--query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu,power.draw", "--format=csv,noheader,nounits"]
        output = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode().strip()
        parts = output.split(", ")
        return {
            "utilization": float(parts[0]), "memoryUsed": float(parts[1]), "memoryTotal": float(parts[2]),
            "temperature": float(parts[3]), "power": float(parts[4]), "active": True
        }
    except: return {"utilization": 0, "memoryUsed": 0, "memoryTotal": 0, "temperature": 0, "power": 0, "active": False}

def get_fleet_disk_usage():
    try:
        ollama_dir = os.path.expanduser("~/.ollama/models")
        if not os.path.exists(ollama_dir): return "N/A"
        process = subprocess.Popen(["du", "-sh", ollama_dir], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, _ = process.communicate()
        return out.decode().split()[0]
    except: return "ERR"

def get_ollama_status():
    try:
        resp = requests.get("http://localhost:11434/api/tags", timeout=1)
        if resp.status_code == 200:
            models = resp.json().get("models", [])
            ps_resp = requests.get("http://localhost:11434/api/ps", timeout=1)
            running = ps_resp.json().get("models", []) if ps_resp.status_code == 200 else []
            return {"online": True, "models": models, "running": running}
        return {"online": False}
    except: return {"online": False}

def get_fleet_config():
    fleet_json_path = os.path.join(PROJECT_ROOT, "config/fleet.json")
    managed = []
    private = []
    
    if os.path.exists(fleet_json_path):
        try:
            with open(fleet_json_path, 'r') as f:
                data = json.load(f)
                for m in data.get("models", []):
                    if m.get("isPrivate"): private.append(m["name"])
                    else: managed.append(m["name"])
        except: pass
    return managed, private

def get_system_state():
    if psutil is None:
        return {"error": "Missing dependency: psutil. Run './acc setup' to fix.", "stats": None}

    cpu = psutil.cpu_percent()
    ram = psutil.virtual_memory()
    gpu = get_gpu_stats()
    disk = get_fleet_disk_usage()
    
    ollama = get_ollama_status()
    managed, private = get_fleet_config()
    
    fleet = []
    if ollama.get("online"):
        installed_names = [m["name"].split(":")[0] for m in ollama["models"]]
        running_names = [r["name"].split(":")[0] for r in ollama["running"]]
        all_m = list(set(managed + private + installed_names))
        all_m.sort()
        
        for name in all_m:
            fleet.append({
                "name": name,
                "isInstalled": name in installed_names,
                "isRunning": name in running_names,
                "type": "PRIV" if name in private else "COMM"
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
        "proxyOnline": False
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
