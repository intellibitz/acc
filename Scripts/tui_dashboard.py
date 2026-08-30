import time
import os
import subprocess
import json
import psutil
import requests
import sys
import threading
import re
import signal
from datetime import datetime
from rich.console import Console
from rich.layout import Layout
from rich.panel import Panel
from rich.table import Table
from rich.text import Text
from rich import box
from rich.progress import Progress, BarColumn, TextColumn, TimeRemainingColumn

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ACC_PATH = os.path.join(PROJECT_ROOT, "acc")
console = Console()

class OllamaDashboard:
    def __init__(self):
        self.activity_log = []
        self.start_time = time.time()
        self.status_msg = "READY"
        self.current_progress = 0.0
        self.is_running = False
        self.dl_method = "hf"
        self.current_process = None
        self._lock = threading.Lock()

    def log_activity(self, message):
        with self._lock:
            timestamp = datetime.now().strftime("%H:%M:%S")
            self.activity_log.append("[{}] {}".format(timestamp, message))
            if len(self.activity_log) > 6: self.activity_log.pop(0)

    def get_gpu_stats(self):
        try:
            cmd = "nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total --format=csv,noheader,nounits"
            output = subprocess.check_output(cmd, shell=True, stderr=subprocess.DEVNULL).decode().strip()
            parts = output.split(", ")
            return {"util": float(parts[0]), "mem_used": float(parts[1]), "mem_total": float(parts[2]), "active": True}
        except: return {"active": False}

    def get_ollama_status(self):
        try:
            resp = requests.get("http://localhost:11434/api/tags", timeout=1)
            if resp.status_code == 200:
                models = resp.json().get("models", [])
                ps_resp = requests.get("http://localhost:11434/api/ps", timeout=1)
                running = ps_resp.json().get("models", []) if ps_resp.status_code == 200 else []
                return {"online": True, "count": len(models), "models": models, "running": running}
            return {"online": False}
        except: return {"online": False}

    def get_fleet_config(self):
        fleet_path = os.path.join(PROJECT_ROOT, "Config/fleet.conf")
        managed_models = []
        if os.path.exists(fleet_path):
            try:
                f = open(fleet_path, 'r')
                content = f.read(); f.close()
                matches = re.findall(r'"([^"|]+\|[^"]+)"', content)
                for m in matches: managed_models.append(m.split('|')[0])
            except: pass
        return managed_models

    def get_params_from_file(self, path):
        params = {}
        if os.path.exists(path):
            try:
                f = open(path, 'r')
                for line in f:
                    if '=' in line and not line.startswith('#'):
                        parts = line.strip().split('=', 1)
                        params[parts[0]] = parts[1]
                f.close()
            except: pass
        return params

    def save_params_to_file(self, path, params):
        try:
            lines = []
            f = open(path, 'r')
            for line in f:
                if '=' in line and not line.startswith('#'):
                    key = line.split('=', 1)[0]
                    if key in params:
                        lines.append("{}={}\n".format(key, params[key]))
                        continue
                lines.append(line)
            f.close()
            f = open(path, 'w'); f.writelines(lines); f.close()
            return True
        except: return False

    def make_header(self) -> Panel:
        grid = Table.grid(expand=True)
        grid.add_column(justify="left", ratio=1); grid.add_column(justify="right", ratio=1)
        uptime_min = int((time.time() - self.start_time) // 60)
        grid.add_row(
            Text("AI COMMAND CENTER [ACC]", style="bold magenta"),
            Text("METHOD: {} | UP: {}m | {}".format(self.dl_method.upper(), uptime_min, self.status_msg), style="bold yellow")
        )
        return Panel(grid, style="white on blue")

    def make_system_stats(self) -> Panel:
        cpu = psutil.cpu_percent(); ram = psutil.virtual_memory(); gpu = self.get_gpu_stats()
        t = Table.grid(padding=(0, 1))
        t.add_column(style="bold cyan", width=12); t.add_column(width=20)
        t.add_row("CPU", "{}%".format(cpu)); t.add_row("RAM", "{:.1f}/{:.0f}G".format(ram.used/(1024**3), ram.total/(1024**3)))
        if gpu["active"]:
            t.add_row("GPU UTIL", "{}%".format(gpu['util'])); t.add_row("GPU VRAM", "{:.1f}/{:.1f}G".format(gpu['mem_used']/1024, gpu['mem_total']/1024))
        return Panel(t, title="[bold white]HARDWARE[/]", border_style="cyan")

    def make_fleet_status(self) -> Panel:
        status = self.get_ollama_status(); managed = self.get_fleet_config()
        table = Table(box=box.SIMPLE, expand=True)
        table.add_column("MODEL", style="bold white"); table.add_column("INST", justify="center"); table.add_column("STATE", justify="center")
        if not status["online"]: return Panel(Text("OFFLINE", style="bold red"), title="FLEET", border_style="red")
        installed_names = []; [installed_names.append(m["name"].split(":")[0]) for m in status["models"]]
        running_names = []; [running_names.append(r["name"].split(":")[0]) for r in status["running"]]
        all_m = managed[:]
        for n in installed_names:
            if n not in all_m: all_m.append(n)
        all_m.sort()
        for name in all_m:
            is_inst = name in installed_names; is_run = name in running_names
            table.add_row(Text(name, style="bold white" if name in managed else "dim white"), "[green]YES[/]" if is_inst else "[red]NO[/]", "[bold green]RUN[/]" if is_run else "[dim]IDLE[/]")
        return Panel(table, title="[bold white]FLEET ({})[/]".format(len(all_m)), border_style="green")

    def make_progress_pane(self) -> Panel:
        if not self.is_running: return Panel(Text("SYSTEM READY", style="dim green"), title="PROGRESS", border_style="dim")
        progress = Progress(TextColumn("[blue]{task.description}"), BarColumn(bar_width=None), "[progress.percentage]{task.percentage:>3.0f}%", TimeRemainingColumn())
        progress.add_task("Executing Task...", total=100, completed=self.current_progress)
        return Panel(progress, title="[bold green]TASK ACTIVE[/]", border_style="green")

    def make_activity_log(self) -> Panel:
        with self._lock: log_text = Text("\n".join(self.activity_log))
        return Panel(log_text, title="[bold white]ACTIVITY LOG[/]", border_style="yellow")

    def make_controls(self) -> Panel:
        if self.is_running:
            controls = Text.assemble((" [X] STOP TASK ", "bold white on red"), "  ", (" [Q] EXIT DASH ", "bold white on red"))
        else:
            controls = Table.grid(expand=True); controls.add_column(justify="center")
            controls.add_row(Text.assemble(
                (" [1] UP ", "bold green"), " ", (" [2] FIT ", "bold magenta"), " ",
                (" [A] ADD ", "bold blue"), " ", (" [R] RMV ", "bold red"), " ",
                (" [S] SRCH ", "bold cyan"), " ", (" [M] METH ", "bold yellow"), " ",
                (" [Y] PRXY ", "bold white on magenta")
            ))
            controls.add_row(Text.assemble(
                (" [T] HW ", "bold magenta"), " ", (" [C] SVC ", "bold blue"), " ",
                (" [P] MDL ", "bold green"), " ", (" [U] UPD ", "bold cyan"), " ",
                (" [K] PRN ", "bold red"), " ", (" [B] BCK ", "bold yellow"), " ",
                (" [O] ROT ", "bold white on blue"), " ", (" [E] SETP ", "bold dim white")
            ))
            controls.add_row(Text.assemble(
                (" [G] AGNT ", "bold white on magenta"), " ", (" [L] LOGS ", "bold white on cyan"), " ",
                (" [I] STAT ", "bold white on green"), " ", (" [?] BRN ", "bold white on green"), " ",
                (" [!] OFF ", "bold red")
            ))
        return Panel(controls, title="[bold white]CONTROLS[/]", border_style="white")

    def generate_layout(self) -> Layout:
        l = Layout(); l.split(Layout(name="header", size=3), Layout(name="main", ratio=1), Layout(name="progress", size=3), Layout(name="controls", size=4), Layout(name="footer", size=8))
        l["main"].split_row(Layout(name="left", ratio=1), Layout(name="right", ratio=1))
        l["header"].update(self.make_header()); l["left"].update(self.make_system_stats()); l["right"].update(self.make_fleet_status()); l["progress"].update(self.make_progress_pane()); l["controls"].update(self.make_controls()); l["footer"].update(self.make_activity_log())
        return l

    def run_task(self, target, query=None, extra=None):
        self.is_running = True; self.status_msg = "RUNNING " + target.upper()
        try:
            cmd = [ACC_PATH, target]
            if target == "up": cmd.append("--method=" + self.dl_method)
            if target in ["search", "add", "remove", "status", "brain"] and query: cmd.append(query)
            if target == "tune-model" and query and extra: cmd.append(query); cmd.append(extra)
            self.current_process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, universal_newlines=True, preexec_fn=os.setsid)
            while True:
                line = self.current_process.stdout.readline()
                if not line and self.current_process.poll() is not None: break
                line = line.strip()
                if not line: continue
                if "%" in line:
                    match = re.search(r'(\d+)%', line)
                    if match: self.current_progress = float(match.group(1))
                if target in ["search", "add", "remove", "status", "tune-model", "tune-hw", "prune", "backup", "update-ollama", "brain", "rotate", "setup"]: self.log_activity(line)
                elif any(x in line.upper() for x in ["PROVISIONING", "DOWNLOADING", "MERGING", "SUCCESS", "ERROR", "FETCH", "MATCH"]): self.log_activity(line)
            self.current_process.wait()
            if self.current_process.returncode == -signal.SIGTERM: self.log_activity("Stopped: " + target)
            else: self.log_activity("Finished: {} ({})".format(target, self.current_process.returncode))
        except: self.log_activity("Fatal Error in " + target)
        self.is_running = False; self.current_progress = 0.0; self.status_msg = "READY"; self.current_process = None

    def tune_svc_menu(self):
        params = self.get_params_from_file(PROJECT_ROOT + "/Config/ollama.env")
        self.log_activity("--- TUNE SERVICE ---")
        keys = []
        for k in params.keys(): keys.append(k)
        idx = 0
        while idx < len(keys): self.log_activity("[{}] {}: {}".format(idx+1, keys[idx], params[keys[idx]])); idx += 1
        choice = console.input("[bold yellow]Select Param (Q to cancel): [/]")
        if choice.lower() == 'q': return
        try:
            val_idx = int(choice) - 1
            if 0 <= val_idx < len(keys):
                key = keys[val_idx]; new_val = console.input("New Value for {}: ".format(key))
                if new_val:
                    params[key] = new_val
                    if self.save_params_to_file(PROJECT_ROOT + "/Config/ollama.env", params):
                        self.log_activity("Updated {}. Running SYNC...".format(key)); threading.Thread(target=self.run_task, args=("sync",), daemon=True).start()
        except: self.log_activity("Invalid selection.")

    def tune_model_menu(self):
        managed = self.get_fleet_config(); self.log_activity("--- TUNE MODEL ---")
        idx = 0
        while idx < len(managed): self.log_activity("[{}] {}".format(idx+1, managed[idx])); idx += 1
        choice = console.input("[bold yellow]Select Model (Q to cancel): [/]")
        if choice.lower() == 'q': return
        try:
            val_idx = int(choice) - 1
            if 0 <= val_idx < len(managed):
                name = managed[val_idx]; params = console.input("Params for {} (semicolon separated): ".format(name))
                if params: threading.Thread(target=self.run_task, args=("tune-model", name, params), daemon=True).start()
        except: self.log_activity("Invalid selection.")

    def tune_hw_menu(self):
        path = PROJECT_ROOT + "/Config/hardware.env"
        params = self.get_params_from_file(path); self.log_activity("--- TUNE HARDWARE ---")
        keys = []
        for k in params.keys(): keys.append(k)
        idx = 0
        while idx < len(keys): self.log_activity("[{}] {}: {}".format(idx+1, keys[idx], params[keys[idx]])); idx += 1
        choice = console.input("[bold yellow]Select Param (Q to cancel): [/]")
        if choice.lower() == 'q': return
        try:
            val_idx = int(choice) - 1
            if 0 <= val_idx < len(keys):
                key = keys[val_idx]; new_val = console.input("New Value for {}: ".format(key))
                if new_val:
                    params[key] = new_val
                    if self.save_params_to_file(path, params):
                        self.log_activity("Updated {}. Running TUNE-HW...".format(key)); threading.Thread(target=self.run_task, args=("tune-hw",), daemon=True).start()
        except: self.log_activity("Invalid selection.")

    def execute_action(self, action):
        action = action.lower()
        if self.is_running:
            if action == 'x': os.killpg(os.getpgid(self.current_process.pid), signal.SIGTERM)
            elif action == 'q': sys.exit(0)
            return
        cmd_map = {
            "1": "up", "2": "fitness", "s": "search", "a": "add", "r": "remove",
            "t": "tune-hw", "c": "tune-svc", "p": "tune-model", "u": "update-ollama",
            "k": "prune", "b": "backup", "g": "agent", "?": "brain",
            "y": "proxy", "o": "rotate", "e": "setup", "i": "status", "l": "logs"
        }
        target = cmd_map.get(action, action)
        if target in ["up", "fitness", "status", "setup", "sync", "update-ollama", "prune", "backup", "brain", "rotate"]:
            threading.Thread(target=self.run_task, args=(target,), daemon=True).start()
        elif target == "search":
            q = console.input("[bold yellow]Search: [/]")
            if q: threading.Thread(target=self.run_task, args=("search", q), daemon=True).start()
        elif target == "add":
            e = console.input("[bold blue]Add String: [/]")
            if e: threading.Thread(target=self.run_task, args=("add", e), daemon=True).start()
        elif target == "remove":
            n = console.input("[bold red]Name: [/]")
            if n: threading.Thread(target=self.run_task, args=("remove", n), daemon=True).start()
        elif target == "tune-hw": self.tune_hw_menu()
        elif target == "tune-svc": self.tune_svc_menu()
        elif target == "tune-model": self.tune_model_menu()
        elif target in ["agent", "proxy", "logs"]:
            if os.getenv("TMUX"): subprocess.run(["tmux", "split-window", "-h", "bash " + ACC_PATH + " " + target])
            else: self.log_activity("Error: Run in TMUX first.")
        elif target == 'm':
            self.log_activity("Pick: (1) HF  (2) PULL  (3) ARIA")
            c = console.input("[bold yellow][1-3]: [/]")
            if c == "1": self.dl_method = "hf"
            elif c == "2": self.dl_method = "pull"
            elif c == "3": self.dl_method = "aria"
            self.log_activity("Method: " + self.dl_method.upper())
        elif target == '!':
            self.log_activity("Shutting down..."); subprocess.run([ACC_PATH, "stop"]); sys.exit(0)
        elif target == 'q': sys.exit(0)
        else: self.log_activity("Cmd: " + action)

def run_dashboard():
    dash = OllamaDashboard(); dash.log_activity("ACC Orchestrator Online")
    while True:
        os.system('clear'); console.print(dash.generate_layout())
        try:
            cmd = console.input("[bold yellow]ACC > [/]")
            if cmd: dash.execute_action(cmd)
        except: break

if __name__ == "__main__":
    run_dashboard()
