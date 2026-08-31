# AI Agent Handover

Welcome to the Acc cockpit. Here is the current architectural status as of late 2026.

## 🗝️ Core Security Rule
**DO NOT allow arbitrary command execution via WebSockets.**
Command execution is handled by `cc.thevar.acc.service.CommandHandler`, which enforces strict whitelisting and routes commands to either internal Kotlin services or sanitized shell executions.

## 🏗️ The Bridge & Container Pattern
Acc follows a **Container-First** architecture.
- **The Kotlin Gateway** and **Python Bridge** run within a unified Docker environment (defined in `gateway/Dockerfile`).
- **Hardware stats** are platform-aware, detecting Apple Silicon (MPS) vs NVIDIA (CUDA) automatically.
- **Resilient Bootstrapping**: The `acc` script prioritizes Docker Compose for a true "Zero-Footprint" experience, falling back to host execution only if Docker is missing.

## 🚀 Resilient Bootstrapping
Acc implements a "True Zero-Effort" universal bootstrapper.
- **Universal OS Support**: The `acc` script auto-detects macOS (Darwin) and Linux, managing dependencies via `brew` or `apt` where possible.
- **Docker-First**: Infrastructure is orchestrated via `docker-compose.yml` with `docker-compose.override.yml` for local GPU passthrough.
- **VENV-Aware**: Supervisor detects local `.venv` for legacy host execution support.

## 🧪 Ongoing Missions
- [x] Implement unit tests for the `CommandHandler` logic in `gateway`.
- [x] Move `ProvisioningService` into a more robust state-machine model in Kotlin.
- [x] Implement real-time GPU memory tracking using the `nvidia-smi` bridge more efficiently.
- [x] Implement "True Zero-Effort" resilient bootstrapper and `.venv` management.
- [x] Pivot to **Container-First** architecture and **Universal macOS support**.
- [ ] Implement integrated E2E journey tests for the Provisioning flow.

See [docs/testing.md](docs/testing.md) for verification commands.
