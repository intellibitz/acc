# AI Agent Handover

Welcome to the Acc cockpit. Here is the current architectural status as of late 2026.

## 🗝️ Core Security Rule
**DO NOT allow arbitrary command execution via WebSockets.**
Command execution is handled by `cc.thevar.acc.service.CommandHandler`, which enforces strict whitelisting and routes commands to either internal Kotlin services or sanitized shell executions.

## 🏗️ The Bridge & Container Pattern
Acc follows a **Container-First, Engine-Neutral** architecture.
- **The Kotlin Gateway** and **Python Bridge** run within a unified Docker environment.
- **Engine Neutrality**: Support for Ollama, LocalAI, and vLLM is abstracted. The Provisioner handles provider-specific registration (e.g., Modelfiles for Ollama, YAMLs for LocalAI).
- **Hardware Tuning**: Abstracted into `hwt.sh` (Hardware Tuner), which optimizes the host system (Kernel, GPU, CPU) regardless of which AI engine is used.

## 🚀 Resilient Bootstrapping
Acc implements a "True Zero-Effort" universal bootstrapper.
- **Universal Python Core**: The system is orchestrated entirely via `acc.py`, ensuring identical behavior across Windows, Linux, and macOS.
- **Docker-First**: Infrastructure is orchestrated via `docker-compose.yml` with `docker-compose.override.yml` for local GPU passthrough.
- **Multi-Engine Fleet**: Users can add any AI agent/engine/model via the `python3 acc.py add` command.

## 🛠️ The Creator Workflow (Zero-Effort CI/CD)
To keep the "Zero-Effort" promise to Normal Users, we use a fully automated release pipeline.
1.  **Develop**: Make changes in a feature branch.
2.  **Verify**: Run `docker compose build acc-gateway` and verify in a standalone test directory.
3.  **Test**: Run `./acc dev test` to auto-increment the RC tag and trigger CI verification.
4.  **Release**: Run `./acc dev release` to branch, tag stable, and publish official artifacts.
5.  **Automation**: GitHub Actions (`release.yml`) will:
    - Build Multi-Platform Docker images (AMD64 & ARM64).
    - Push images to `intellibitz/acc-gateway`.
    - Create a GitHub Release with the latest `acc` script and `docker-compose.yml`.

## 🧪 Ongoing Missions
- [x] Pivot to **Container-First** architecture and **Full Universal OS support** (Windows, Linux, macOS) via unified **Python Orchestration** (`acc.py`).
- [x] Implement integrated `acc uninstall` for total system restoration.
- [x] Implement **On-Demand Agent Spawning** for Multi-Engine fleets (Local & Cloud).
- [x] Refactor into **AI-Neutral Folder Structure** (`registry/`, `.cache/`).
- [x] Pivot to **"Everything-in-Docker" Architecture**. The cockpit is now a single container that manages engines, provisioning, and agents internally via the Docker socket.
- [x] Zero-dependency launch: Users only need Docker to run the entire platform.

See [docs/testing.md](docs/testing.md) for verification commands.
