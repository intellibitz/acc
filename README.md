# 🚀 AI Command Center (acc)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Linux](https://img.shields.io/badge/Platform-Linux%20%2F%20WSL2-blue.svg)](https://ubuntu.com/)
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
- **Master Orchestrator**: Just type `acc` to start.
- **Visual Dashboard**: Real-time TUI tracking CPU, RAM, and NVIDIA GPU metrics.
- **Backend Agnostic**: Native support for **Ollama**, **LocalAI**, and **vLLM** via LiteLLM abstraction.
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
- **`common/`**: The **Registry**. Shared type-safe protocols and models ensuring backend-to-frontend integrity, verified by automated JVM tests.
- **`tooling/`**: The **Workshop**. Low-level scripts for project maintenance and benchmarking.

---

## 🚀 Quick Start (True Zero Effort)

Run the one-liner to install and launch your cockpit instantly:

```bash
curl -sSL https://raw.githubusercontent.com/intellibitz/acc/main/install.sh | bash
```

---

## 🏗️ The "Zero-Effort" Workflow
When you run `acc` for the first time:
1.  **Instant Visuals**: The dashboard launches immediately in your browser.
2.  **Automated Audit**: acc performs a full system audit, auto-installing missing tools (`tmux`, `jq`, `java`) and managing an isolated **Python .venv** automatically.
3.  **Visual Bootstrapping**: Status updates are streamed directly to your screen while hardware tuning and background frontend builds occur.
4.  **Elite Provisioning**: Models are downloaded and optimized using hardware-aware logic within the Kotlin Gateway.
5.  **Persistent Monitoring**: High-efficiency hardware tracking via a persistent JSON stream bridge with integrated health diagnostics.
6.  **Unified Control**: Control everything—provision models, tune hardware, and watch agent thought streams—directly from the dashboard.

## 🛠️ Orchestrator Commands
The `acc` script acts as a smart dispatcher for your workstation:

### 🎮 User (The Pilot)
| Command | Action |
| :--- | :--- |
| **`./acc`** | Launch the Visual Dashboard and start infrastructure. |
| **`./acc update`** | One-click upgrade: syncs code, runs setup, and restarts. |
| **`./acc chat`** | Launch an interactive session with any model. |
| **`./acc stop`** | Gracefully shutdown all services and agents. |

### 🔧 Maintenance (The Mechanic)
| Command | Action |
| :--- | :--- |
| **`./acc maint kill`** | Surgical purge of stale Java/Gradle/Kotlin processes. |
| **`./acc maint prune`** | Reclaim disk space by cleaning the fleet and downloads. |
| **`./acc maint rotate`** | Rotate and clean service logs. |

### 📐 Developer (The Architect)
| Command | Action |
| :--- | :--- |
| **`./acc dev push`** | Auto-stage and push project changes to GitHub. |
| **`./acc dev benchmark`** | Run the token-per-second (TPS) performance suite. |

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
- **Environment Audit**: The `./acc setup` command performs a pre-flight health check of your kernel, drivers, and toolchain.
- **Visual Parity**: UI components are developed with Material 3 tokens to ensure consistent rendering across Wasm, Android, and Desktop.

---

## 🛠️ Prerequisites
- **OS**: Linux (Ubuntu/Debian) or WSL2.
- **Hardware**: NVIDIA GPU (Optional, but highly recommended).
- **Auto-Managed**: `tmux`, `jq`, `python3-venv`, `OpenJDK 21+` (Attempted auto-installation on setup).
- **Manual**: `docker` (if using external providers).

---

## 🤝 Contributing
acc is built for the community. If you have ideas for better orchestration or new model superpowers, check out our [CONTRIBUTING.md](CONTRIBUTING.md).

## 🛡️ License
Distributed under the MIT License. See `LICENSE` for more information.

---
*Built with ❤️ for the AI Community. Unlock your hardware's true potential.*
