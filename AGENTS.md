# AI Agent Handover

Welcome to the Acc cockpit. Here is the current architectural status as of late 2026.

## 🗝️ Core Security Rule
**DO NOT allow arbitrary command execution via WebSockets.**
Command execution is handled by `cc.thevar.acc.service.CommandHandler`, which enforces strict whitelisting and routes commands to either internal Kotlin services or sanitized shell executions.

## 🏗️ The Bridge Pattern
Hardware stats are fetched via a persistent `brain/system_bridge.py` stream.
- **The Kotlin side** (via `SupervisorService`) consumes this stream and broadcasts to UI/System sessions.
- **The Python side** runs a persistent loop emitting JSON every 2 seconds.
- **Health Diagnostics**: The bridge catches `ModuleNotFoundError` and emits structured JSON errors for the Gateway to display as actionable status messages.

## 🚀 Resilient Bootstrapping
Acc implements a "True Zero-Effort" bootstrapper via the `acc` shell script and Gateway's "Bootstrap UI".
- **Environment Management**: The `acc` script auto-installs system tools (`tmux`, `jq`, `java`) and manages a local `.venv` for Python.
- **Non-Blocking Build**: The frontend build is triggered in the background to avoid blocking Gateway startup.
- **VENV-Aware Supervisor**: `SupervisorService` automatically detects and uses the local `.venv` for Python workers.

## 🧪 Ongoing Missions
- [x] Implement unit tests for the `CommandHandler` logic in `gateway`.
- [x] Move `ProvisioningService` into a more robust state-machine model in Kotlin.
- [x] Implement real-time GPU memory tracking using the `nvidia-smi` bridge more efficiently via persistent stream.
- [x] Implement "True Zero-Effort" resilient bootstrapper and `.venv` management.
- [ ] Implement integrated E2E journey tests for the Provisioning flow.

See [docs/testing.md](docs/testing.md) for verification commands.
