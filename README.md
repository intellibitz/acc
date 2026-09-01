# 🚀 AI Command Center (acc)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Universal](https://img.shields.io/badge/Platform-Linux%20%7C%20macOS%20%7C%20Windows-blue.svg)](https://github.com/intellibitz/acc)
[![Engine: Backend-Agnostic](https://img.shields.io/badge/Engine-Ollama%20%7C%20LocalAI%20%7C%20vLLM-blueviolet.svg)](https://litellm.ai/)
[![Frontend: Kotlin Multiplatform](https://img.shields.io/badge/Frontend-Compose%20Wasm%20%7C%20Android%20%7C%20Desktop-green.svg)](https://kotlinlang.org/docs/multiplatform.html)

**AI Command Center (acc)** is the elite orchestration environment for AI Agents and LLMs. It
transforms your workstation into a professional-grade AI cockpit with **zero-effort** provisioning,
real-time visual monitoring, and a high-performance **Kotlin Multiplatform** frontend.

Run any open-source model—from Phi-3 to Llama 3.1 70B—with a single command and watch their "
thoughts" stream in real-time.

---

## 🌟 Vision: Zero Effort, Infinite Intelligence

acc is designed for the developer who wants to *use* AI, not fight with dependencies.

- **Unified Visual Interface**: A high-performance Compose Multiplatform app (Wasm, Android,
  Desktop) for real-time agent observability.
- **Thought Stream**: Visualize the agent's internal reasoning process (the "hidden thoughts") as
  they happen.
- **Tool Visualization**: Transparently monitor tool calls (shell commands, file edits) in a
  terminal-style interface.
- **Master Orchestrator**: A unified **Python-based** orchestrator (`acc.py`) for cross-platform
  consistency. Just type `acc` to start.
- **On-Demand Spawning**: Instantly spawn specialized agent bridges (NLE, Architect, Researcher) for
  any model in your fleet.
- **Visual Dashboard**: Real-time TUI tracking CPU, RAM, and hardware metrics (NVIDIA CUDA / Apple
  Silicon) via the **HWT** (Hardware Tuner).
- **Backend Agnostic**: Native support for **Ollama**, **LocalAI**, and **vLLM**, plus integration
  with cloud providers like **OpenAI**, **Anthropic**, and **Gemini**.
- **Multi-Engine Fleet**: Mix and match local and cloud models in a unified interface.
- **Private Fleet**: Separate your paid/private models using the git-ignored
  `config/private_fleet.conf`.
- **Smart Provisioning**: Resilient multi-connection downloads via **HF-CLI** with automatic
  registration via **Ollama HTTP API**.
- **Sandboxed Security**: Hardened console with strict API-level command white-listing, preventing
  host-level Remote Code Execution (RCE).

---

## 🏗️ Project Structure (Standard Format)

acc is organized into a clean, professional architecture:

- **`gateway/`**: The **Manager** (Control Plane). A modular Ktor hub powered by **Koin DI**. It supervises all workers, handles provisioning (Kotlin-native), and serves the UI. Features modular routing, a dedicated `MonitoringService` for real-time telemetry, and a hardened `CommandHandler`.
- **`frontend/`**: The **Deck** (UI). A Compose Multiplatform app providing real-time observability. Powered by **Koin DI** for cross-platform ViewModel management. Features an **Adaptive UI** that automatically scales from mobile to desktop.
- **`brain/`**: The **Intelligence** layer. Managed Python workers for agents and a persistent
  system metrics bridge, communicating via type-safe JSON protocols.
- **`common/`**: The **Registry**. Shared type-safe protocols and models ensuring
  backend-to-frontend integrity, verified by automated JVM tests. Includes **HWT** and **Provisioner
  ** logic.
- **`registry/`**: The **Vault**. Central storage for engine-neutral manifests, model parameters,
  and provider configurations (e.g., Ollama Modelfiles, LocalAI YAMLs).
- **`tooling/`**: The **Workshop**. Low-level scripts for project maintenance, verification, and benchmarking.
- **`docs/`**: The **Library**. Comprehensive documentation, including architectural deep-dives (`/architecture`) and testing guides.

---

## 🚀 Quick Start (Folder Sandbox Mode)

`acc` runs as a folder-based sandbox. No Docker required.

```bash
# 1. Setup environment (creates .venv, downloads deps)
python3 acc.py setup

# 2. Launch the Cockpit
python3 acc.py
```

Once the cockpit is up, open **`http://localhost:8333`** to manage your fleet, engines, and agents.

---

## 🏗️ The Folder Sandbox Architecture

`acc` is designed to be self-contained within its project directory:

1. **Host Native**: Runs directly on your host using Java and Python.
2. **Local Engines**: Manages AI engines (like Ollama) as local background processes.
3. **Isolated Data**: All models, configs, and logs are stored in `data/`, `registry/`, and `logs/` folders.
4. **Zero Footprint**: Deleting the `acc` folder removes all traces from your system.

## 🛠️ Orchestrator Commands

The `acc` script acts as a smart dispatcher for your workstation:

### 🎮 User (The Pilot)

| Command                        | Action                                                |
|:-------------------------------|:------------------------------------------------------|
| **`python3 acc.py`**           | Launch the Visual Dashboard and start infrastructure. |
| **`python3 acc.py stop`**      | Gracefully shutdown all services and agents.          |
| **`python3 acc.py uninstall`** | Completely remove Acc and reclaim all disk space.     |
| **`python3 acc.py add`**       | Add a new model/provider to your fleet.               |
| **`python3 acc.py optimize`**  | Tune host hardware for AI workloads (Linux only).     |

### 🔧 Maintenance (The Mechanic)

| Command                                | Action                                                 |
|:---------------------------------------|:-------------------------------------------------------|
| **`python3 acc.py stop`**              | Gracefully shutdown all services and agents.           |
| **`python3 acc.py uninstall --force`** | Reclaim disk space by cleaning the fleet and registry. |

### 📐 Developer (The Architect)

| Command                            | Action                                                |
|:-----------------------------------|:------------------------------------------------------|
| **`python3 acc.py dev push`**      | Auto-stage and push project changes to GitHub.        |
| **`python3 acc.py dev test`**      | Tag and trigger a Test/RC release workflow.           |
| **`python3 acc.py dev release`**   | Branch, tag stable, and trigger the official release. |
| **`python3 acc.py dev benchmark`** | Run the token-per-second (TPS) performance suite.     |

## 🛡️ Self-Healing & Secure Architecture

acc is built like a production container orchestrator:

- **Modular Control Plane**: The Gateway uses **Koin Dependency Injection** and **AutoCloseable** service lifecycles to ensure high testability, clean resource management, and zero "zombie" processes.
- **API Orchestration**: All model management is handled via secure HTTP APIs to engines (Ollama/vLLM), rather than brittle CLI calls.
- **Resilient Telemetry**: A dedicated `MonitoringService` manages WebSocket streams with structured error handling and SLF4J logging, ensuring metrics reach the UI reliably.
- **Auto-Heal & Forced Kill**: If a worker crashes, the Manager restarts it. Unresponsive workers are forcibly terminated to reclaim system resources.
- **Console Sandbox**: The terminal interface is strictly restricted to non-privileged orchestration
  tasks. Sensitive operations like `setup` or `tune-hw` are host-only to prevent unauthorized system
  modification.
- **Secret Management**: Sensitive data, such as keystore passwords, are managed via environment
  variables (`ACC_KEYSTORE_PASSWORD`).
- **Bootstrap UI**: In fresh environments, the Manager serves a live "Bootstrap Page" showing
  real-time build and setup progress until the cockpit is ready.

## 🧪 Reliability & Testing

acc maintains high stability through a multi-layered testing strategy:

- **Protocol Integrity**: Automated tests in `:common` verify that the JSON communication between
  Python workers, the Kotlin Gateway, and the Compose UI never breaks.
- **Environment Audit**: The `python3 acc.py setup` command performs a pre-flight health check of
  your toolchain.
- **Visual Parity**: UI components are developed with Material 3 tokens to ensure consistent
  rendering across Wasm, Android, and Desktop.

---

## 🛠️ Prerequisites

- **OS**: Linux (Ubuntu/Debian), macOS (Apple Silicon/Intel), or Windows 10/11.
- **JDK 21+**: Required to run the Gateway.
- **Python 3.10+**: Required for agents and bridges.
- **Ollama**: (Optional) For local model execution.

---

## 🤝 Contributing

acc is built for the community. If you have ideas for better orchestration or new model superpowers,
check out our [CONTRIBUTING.md](CONTRIBUTING.md).

### 🛠️ Creator Workflow

1. Verify changes: `./gradlew :gateway:assemble`
2. Launch: `python3 acc.py`

## 🛡️ License

Distributed under the MIT License. See `LICENSE` for more information.

---
*Built with ❤️ for the AI Community. Unlock your hardware's true potential.*
