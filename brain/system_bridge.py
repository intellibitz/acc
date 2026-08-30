import psutil
import subprocess
import json
import os
import requests
import re

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def get_gpu_stats():
    try:
        cmd = "nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu,power.draw --format=csv,noheader,nounits"
        output = subprocess.check_output(cmd, shell=True, stderr=subprocess.DEVNULL).decode().strip()
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
        cmd = "du -sh {} | awk '{{print $1}}'".format(ollama_dir)
        return subprocess.check_output(cmd, shell=True).decode().strip()
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
    fleet_path = os.path.join(PROJECT_ROOT, "config/fleet.conf")
    private_path = os.path.join(PROJECT_ROOT, "config/private_fleet.conf")
    managed = []
    private = []
    
    def parse_conf(path, target_list):
        if os.path.exists(path):
            try:
                with open(path, 'r') as f:
                    content = f.read()
                    matches = re.findall(r'"([^"|]+\|[^"|]+)\|', content)
                    for m in matches: target_list.append(m.split('|')[1])
            except: pass

    parse_conf(fleet_path, managed)
    parse_conf(private_path, private)
    return managed, private

def get_partial_downloads():
    partials = []
    dl_dir = os.path.join(PROJECT_ROOT, "downloads")
    if os.path.exists(dl_dir):
        for d in os.listdir(dl_dir):
            path = os.path.join(dl_dir, d)
            if os.path.isdir(path):
                try:
                    files = os.listdir(path)
                    if any(f.endswith(".part") or ".part" in f for f in files):
                        partials.append(d)
                except: pass
    return partials

def get_system_state():
    cpu = psutil.cpu_percent()
    ram = psutil.virtual_memory()
    gpu = get_gpu_stats()
    disk = get_fleet_disk_usage()
    
    ollama = get_ollama_status()
    managed, private = get_fleet_config()
    partials = get_partial_downloads()
    
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

    proxy_online = False
    try:
        resp = requests.get("http://localhost:4000/health", timeout=0.5)
        proxy_online = resp.status_code == 200
    except: pass

    return {
        "stats": {
            "cpuUtilization": cpu,
            "ramUsed": ram.used / (1024**3),
            "ramTotal": ram.total / (1024**3),
            "gpu": gpu,
            "diskUsage": disk
        },
        "fleet": fleet,
        "partialDownloads": partials,
        "proxyOnline": proxy_online,
        "statusMsg": "READY"
    }

if __name__ == "__main__":
    print(json.dumps(get_system_state()))
