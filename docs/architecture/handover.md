# AI Agent Handover

Welcome to the Acc cockpit. Here is the current architectural status as of late 2026.

## 🗝️ Core Security Rule
**DO NOT allow arbitrary command execution via WebSockets.**
Command execution is handled by `cc.thevar.acc.service.CommandHandler`, which enforces strict whitelisting. Privileged operations (`setup`, `tune-hw`, `optimize`) are **explicitly blocked** from the UI and must be run manually via `acc.py` on the host to maintain security boundaries.

## 🏗️ The Bridge & Folder Sandbox Pattern
Acc is a **Pure Folder-Sandbox Intelligence Layer**.
- **Self-Contained Gateway**: The cockpit is a single project directory containing the Manager (Kotlin), the Bridge (Python), and the UI (Wasm).
- **API Orchestration**: The Gateway manages engines (Ollama, vLLM) via HTTP APIs. It expects engines to be running locally or reachable via network.
- **Zero Footprint**: The user's system remains untouched outside the project folder. All state is maintained within `data/`, `registry/`, and `logs/`.

## 🛠️ The Creator Workflow (Zero-Effort CI/CD)
To keep the "Zero-Effort" promise to Normal Users, we use a fully automated release pipeline.
1.  **Develop**: Make changes in a feature branch.
2.  **Verify**: Run `./gradlew :gateway:assemble`.
3.  **Test**: Run `python3 acc.py dev test` to auto-increment the RC tag and trigger CI verification.
4.  **Release**: Run `python3 acc.py dev release` to branch, tag stable, and publish official artifacts.
5.  **Automation**: GitHub Actions (`release.yml`) builds the distribution ZIP and official releases.

## 🧪 Ongoing Missions
- [x] Pivot to **"Folder-Sandbox" Architecture**.
- [x] Implement integrated `acc uninstall` (Safe data purge).
- [x] distribution artifacts support.
- [x] Purge all legacy Docker, Bash and PowerShell technical debt.
- [x] 0 Shell Dependency Architecture (Migrated all scripts to Python/Kotlin).
- [x] Engine-on-Demand: Control local engines (Ollama) from the UI via APIs.
- [x] API-driven Zero-Footprint Provisioning.
- [ ] Implement integrated E2E journey tests for the Provisioning flow.

See [../testing.md](../testing.md) for verification commands.
