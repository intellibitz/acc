# AI Agent Handover

Welcome to the Acc cockpit. Here is the current architectural status as of late 2026.

## 🗝️ Core Security Rule
**DO NOT allow arbitrary command execution via WebSockets.**
Command execution is handled by `cc.thevar.acc.service.CommandHandler`, which enforces strict whitelisting and routes commands to either internal Kotlin services or sanitized shell executions.

## 🏗️ The Bridge Pattern
Hardware stats are fetched via a persistent `brain/system_bridge.py` stream.
- **The Kotlin side** (via `SupervisorService`) consumes this stream and broadcasts to UI/System sessions.
- **The Python side** runs a persistent loop emitting JSON every 2 seconds to avoid spawn overhead.
- **Avoid Regex**: We have removed regex patching. Always use structured JSON merging.

## 📱 UI Modularization
The UI is split into:
- `cc.thevar.acc.ui.Sidebar`: System controls.
- `cc.thevar.acc.ui.ConsoleView`: Monospace terminal.
- `cc.thevar.acc.ui.Dashboard`: Stats and Agent stream.
- `cc.thevar.acc.ui.Theme`: Material 3 design tokens.

## 🧪 Ongoing Missions
- [x] Implement unit tests for the `CommandHandler` logic in `gateway`.
- [x] Move `ProvisioningService` into a more robust state-machine model in Kotlin.
- [x] Implement real-time GPU memory tracking using the `nvidia-smi` bridge more efficiently via persistent stream.
- [ ] Implement integrated E2E journey tests for the Provisioning flow.

See [docs/testing.md](docs/testing.md) for verification commands.
