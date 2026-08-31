# 🚀 AI Command Center (acc)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Universal](https://img.shields.io/badge/Platform-Linux%20%7C%20macOS%20%7C%20Windows-blue.svg)](https://docker.com/)
[![Engine: Backend-Agnostic](https://img.shields.io/badge/Engine-Ollama%20%7C%20LocalAI%20%7C%20vLLM-blueviolet.svg)](https://litellm.ai/)
[![Frontend: Kotlin Multiplatform](https://img.shields.io/badge/Frontend-Compose%20Wasm%20%7C%20Android%20%7C%20Desktop-green.svg)](https://kotlinlang.org/docs/multiplatform.html)

**AI Command Center (acc)** is the elite orchestration environment for AI Agents and LLMs. It transforms your workstation into a professional-grade AI cockpit with **zero-effort** provisioning, real-time visual monitoring, and a high-performance **Kotlin Multiplatform** frontend.

Run any open-source model—from Phi-3 to Llama 3.1 70B—with a single command and watch their "thoughts" stream in real-time.

---

## 🌟 Vision: Zero Effort, Infinite Intelligence
acc is designed for the developer who wants to *use* AI, not fight with dependencies.
- **Unified Visual Interface**: A high-performance Compose Multiplatform app (Wasm, Android, Desktop) for real-time agent observability.
- **Thought Stream**: Visualize the agent's internal reasoning process (the "hidden thoughts") as they happen.
- **Tool Visualization**: Transparently monitor tool calls (shell commands, file edits) in a terminal-style interface.
- **Master Orchestrator**: A unified **Python-based** orchestrator (`acc.py`) for cross-platform consistency. Just type `acc` to start.
- **On-Demand Spawning**: Instantly spawn specialized agent bridges (NLE, Architect, Researcher) for any model in your fleet.
- **Visual Dashboard**: Real-time TUI tracking CPU, RAM, and hardware metrics (NVIDIA CUDA / Apple Silicon) via the **HWT** (Hardware Tuner).
- **Backend Agnostic**: Native support for **Ollama**, **LocalAI**, and **vLLM**, plus integration with cloud providers like **OpenAI**, **Anthropic**, and **Gemini**.
- **Multi-Engine Fleet**: Mix and match local and cloud models in a unified interface.
- **Private Fleet**: Separate your paid/private models using the git-ignored `config/private_fleet.conf`.
- **Smart Provisioning**: Resilient multi-connection downloads via official **HF-CLI**.
- **Elite Tuning**: Automated kernel and driver optimization for maximum performance.
- **Sandboxed Security**: Hardened console with command white-listing and environment-based secret management.

---

## 🏗️ Project Structure (Standard Format)
acc is organized into a clean, professional architecture:
- **`gateway/`**: The **Manager** (Control Plane). A high-performance Ktor hub that supervises all workers, handles provisioning (Kotlin-native), and serves the UI. Features a hardened `CommandHandler` and a stream-based hardware-stats consumer.
- **`frontend/`**: The **Deck** (UI). A Compose Multiplatform app providing real-time observability. Modularized into discrete components (Sidebar, Console, Dashboard) with full **Edge-to-Edge** support.
- **`brain/`**: The **Intelligence** layer. Managed Python workers for agents and a persistent system metrics bridge, communicating via type-safe JSON protocols.
- **`common/`**: The **Registry**. Shared type-safe protocols and models ensuring backend-to-frontend integrity, verified by automated JVM tests. Includes **HWT** and **Provisioner** logic.
- **`registry/`**: The **Vault**. Central storage for engine-neutral manifests, model parameters, and provider configurations (e.g., Ollama Modelfiles, LocalAI YAMLs).
- **`tooling/`**: The **Workshop**. Low-level scripts for project maintenance and benchmarking.

---

## 🚀 Quick Start (True Zero Effort)

Acc is **Unified & Universal**. Run the installer for your OS to launch your cockpit instantly:

### 🐧 Linux, 🍎 macOS & 🪟 Windows
```bash
docker run -p 8333:8333 -v /var/run/docker.sock:/var/run/docker.sock intellibitz/acc-gateway
```
Once the cockpit is up, open **`http://localhost:8333`** to manage your fleet, engines, and agents with total zero-effort.

---

## 🏗️ The "Zero-Footprint" Architecture
Acc is designed to leave **no traces** on your system:
1.  **Pure Docker**: The entire cockpit runs in a single container.
2.  **No Host Dependencies**: You don't need Python, Java, or Node installed on your workstation.
3.  **Engine Management**: The cockpit manages its own AI engines (Ollama, vLLM) via the Docker socket.
4.  **Ephemeral Registry**: Local model manifests and parameters are stored in Docker volumes.

## 🛠️ Orchestrator Commands
The `acc` script acts as a smart dispatcher for your workstation:

### 🎮 User (The Pilot)
| Command | Action |
| :--- | :--- |
| **`python3 acc.py`** | Launch the Visual Dashboard and start infrastructure. |
| **`python3 acc.py stop`** | Gracefully shutdown all services and agents. |
| **`python3 acc.py uninstall`** | Completely remove Acc and reclaim all disk space. |
| **`python3 acc.py add`** | Add a new model/provider to your fleet. |
| **`python3 acc.py optimize`** | Tune host hardware for AI workloads (Linux only). |

### 🔧 Maintenance (The Mechanic)
| Command | Action |
| :--- | :--- |
| **`python3 acc.py stop`** | Gracefully shutdown all services and agents. |
| **`python3 acc.py uninstall --force`** | Reclaim disk space by cleaning the fleet and registry. |

### 📐 Developer (The Architect)
| Command | Action |
| :--- | :--- |
| **`python3 acc.py dev push`** | Auto-stage and push project changes to GitHub. |
| **`python3 acc.py dev test`** | Tag and trigger a Test/RC release workflow. |
| **`python3 acc.py dev release`** | Branch, tag stable, and trigger the official release. |
| **`python3 acc.py dev benchmark`** | Run the token-per-second (TPS) performance suite. |

## 🛡️ Self-Healing & Secure Architecture
acc is built like a production container orchestrator:
- **Manager-Worker Model**: The Gateway acts as a Control Plane, supervising all background processes.
- **Auto-Heal**: If a metrics bridge or agent crashes, the Manager automatically detects and restarts it.
- **Health Diagnostics**: The system bridge identifies missing Python dependencies and propagates actionable "fix-it" messages to the UI status bar.
- **Console Sandbox**: The terminal interface is restricted to a white-list of safe maintenance and provisioning commands, preventing Remote Code Execution (RCE).
- **Secret Management**: Sensitive data, such as keystore passwords, are managed via environment variables (`ACC_KEYSTORE_PASSWORD`).
- **Bootstrap UI**: In fresh environments, the Manager serves a live "Bootstrap Page" showing real-time build and setup progress until the cockpit is ready.

## 🧪 Reliability & Testing
acc maintains high stability through a multi-layered testing strategy:
- **Protocol Integrity**: Automated tests in `:common` verify that the JSON communication between Python workers, the Kotlin Gateway, and the Compose UI never breaks.
- **Environment Audit**: The `python3 acc.py setup` command performs a pre-flight health check of your toolchain.
- **Visual Parity**: UI components are developed with Material 3 tokens to ensure consistent rendering across Wasm, Android, and Desktop.

---

## 🛠️ Prerequisites
- **OS**: Linux (Ubuntu/Debian), macOS (Apple Silicon/Intel), or Windows 10/11.
- **Engine**: **Docker Desktop** (Required for "Zero-Footprint" mode).
- **Hardware**: NVIDIA GPU, Apple Silicon, or generic CPU.

---

## 🤝 Contributing
acc is built for the community. If you have ideas for better orchestration or new model superpowers, check out our [CONTRIBUTING.md](CONTRIBUTING.md).

### 🛠️ Creator Workflow
1.  Verify changes: `docker compose build acc-gateway`
2.  Tag release: `git tag vX.Y.Z && git push --tags`
3.  Automation handles the rest!

## 🛡️ License
Distributed under the MIT License. See `LICENSE` for more information.

---
*Built with ❤️ for the AI Community. Unlock your hardware's true potential.*
