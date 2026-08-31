# AI Agent Handover

Welcome to the Acc cockpit. Here is the current architectural status as of late 2026.

## 🗝️ Core Security Rule
**DO NOT allow arbitrary command execution via WebSockets.**
Command execution is handled by `cc.thevar.acc.service.CommandHandler`, which enforces strict whitelisting and routes commands to `acc.py` within the secure container.

## 🏗️ The Bridge & Container Pattern
Acc is a **Pure Containerized Intelligence Layer**.
- **Self-Contained Gateway**: The cockpit is a single Docker image containing the Manager (Kotlin), the Bridge (Python), and the UI (Wasm).
- **Host Autonomy**: The container manages AI engines (Ollama, vLLM) via a mounted Docker socket. No host binaries required.
- **Zero Footprint**: The user's system remains untouched. All state is maintained within Docker volumes or mounted config directories.

## 🛠️ The Creator Workflow (Zero-Effort CI/CD)
To keep the "Zero-Effort" promise to Normal Users, we use a fully automated release pipeline.
1.  **Develop**: Make changes in a feature branch.
2.  **Verify**: Run `docker compose build acc-gateway`.
3.  **Test**: Run `python3 acc.py dev test` to auto-increment the RC tag and trigger CI verification.
4.  **Release**: Run `python3 acc.py dev release` to branch, tag stable, and publish official artifacts.
5.  **Automation**: GitHub Actions (`release.yml`) builds multi-platform Docker images and official releases.

## 🧪 Ongoing Missions
- [x] Pivot to **"Everything-in-Docker" Architecture**.
- [x] Implement integrated `acc uninstall` (Safe data purge).
- [x] Multi-platform image support (x86_64, ARM64).
- [x] Purge all legacy Bash and PowerShell technical debt.
- [x] Engine-on-Demand: Control local engines (Ollama) from the UI.
- [ ] Implement integrated E2E journey tests for the Provisioning flow.

See [docs/testing.md](docs/testing.md) for verification commands.
