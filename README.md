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
- **Smart Provisioning**: Resilient multi-connection downloads (Aria2/HF-CLI).
- **Elite Tuning**: Automated kernel and driver optimization for maximum performance.

---

## 🏗️ Project Structure (Standard Format)
acc is organized into a clean, professional architecture:
- **`frontend/`**: The "Deck" (UI). Contains `composeApp` (shared logic) and platform targets (`android`, `desktop`, `web`).
- **`gateway/`**: The Ktor-based heart (WebSocket & Provisioning Hub). Replaces brittle bash logic with type-safe Kotlin services.
- **`brain/`**: The intelligence layer containing Python agents and system metrics bridge.
- **`common/`**: Shared protocols and data models (Kotlin Multiplatform).
- **`tooling/`**: Maintainer and Developer utility scripts.

---

## 🚀 Quick Start (Zero Effort)

```bash
# 1. Clone the cockpit
git clone https://github.com/intellibitz/acc.git
cd acc

# 2. Start the magic
./acc
```

---

## 🏗️ The "Zero-Effort" Workflow
When you run `acc` for the first time:
1.  **Instant Visuals**: The dashboard launches immediately in your browser.
2.  **Visual Bootstrapping**: acc performs a full dependency audit and hardware tuning while streaming status updates directly to your screen.
3.  **Elite Provisioning**: Models are downloaded and optimized using hardware-aware logic within the Kotlin Gateway.
4.  **Unified Control**: Control everything—provision models, tune hardware, and watch agent thought streams—from one place.

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

---

## ⌨️ Dashboard Controls (TUI)
The visual dashboard provides a centralized hub for your entire fleet:

| Key | Action | Logic |
| :--- | :--- | :--- |
| **`1`** | **Provision** | Auto-download and optimize the latest fleet models. |
| **`V`** | **Sync** | Synchronize service configurations and model manifests. |
| **`Z`** | **Auto-Scale** | Hardware-aware fleet optimization based on VRAM/RAM. |
| **`J`** | **Benchmark** | Track TPS (Tokens/Sec) and TTFT (Time to First Token). |
| **`W`** | **Chat** | Launch an interactive side-by-side chat session with any model. |
| **`Y`** | **Proxy** | Start the LiteLLM gateway for OpenAI-compatible API access. |
| **`G`** | **Agent** | Launch the Autonomous Architect AI lead agent. |
| **`T`** | **Tune HW** | Apply low-level Linux kernel optimizations for AI performance. |
| **`S`** | **HF Search** | Discovery models directly from Hugging Face. |
| **`A`** | **Add** | Add a new model to your managed fleet string. |

---

## 🛠️ Prerequisites
- **OS**: Linux (Ubuntu/Debian) or WSL2.
- **Hardware**: NVIDIA GPU (Optional, but highly recommended).
- **Toolchain**: `docker`, `tmux`, `aria2c`, `jq`, `python3`, `OpenJDK 17+`.

---

## 🤝 Contributing
acc is built for the community. If you have ideas for better orchestration or new model superpowers, check out our [CONTRIBUTING.md](CONTRIBUTING.md).

## 🛡️ License
Distributed under the MIT License. See `LICENSE` for more information.

---
*Built with ❤️ for the AI Community. Unlock your hardware's true potential.*
