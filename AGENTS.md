# AI Agent Handover

Welcome to the Acc cockpit. Here is the current architectural status as of late 2026.

## 🗝️ Core Security Rule
**DO NOT allow arbitrary command execution via WebSockets.**
The `/ws/console` endpoint is white-listed in `Application.kt`. If you add new functionality that requires terminal access, add the command to the `allowedCommands` set and ensure it is sanitized.

## 🏗️ The Bridge Pattern
Hardware stats are fetched via `brain/system_bridge.py`.
- **The Kotlin side** deserializes this into `SystemState` using `kotlinx.serialization`.
- **The Python side** must output a raw JSON object matching the `SystemStats` and `FleetStatus` fields.
- **Avoid Regex**: We have removed regex patching. Always use structured JSON merging.

## 📱 UI Modularization
The UI is split into:
- `cc.thevar.acc.ui.Sidebar`: System controls.
- `cc.thevar.acc.ui.ConsoleView`: Monospace terminal.
- `cc.thevar.acc.ui.Dashboard`: Stats and Agent stream.
- `cc.thevar.acc.ui.Theme`: Material 3 design tokens.

## 🧪 Ongoing Missions
- [ ] Implement unit tests for the `CommandHandler` logic in `gateway`.
- [ ] Move `ProvisioningService` into a more robust state-machine model.
- [ ] Implement real-time GPU memory tracking using the `nvidia-smi` bridge more efficiently.

See [docs/testing.md](docs/testing.md) for verification commands.
