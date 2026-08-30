# 🚀 AI Command Center (acc)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Linux](https://img.shields.io/badge/Platform-Linux%20%2F%20WSL2-blue.svg)](https://ubuntu.com/)
[![Engine: Backend-Agnostic](https://img.shields.io/badge/Engine-Ollama%20%7C%20LocalAI%20%7C%20vLLM-blueviolet.svg)](https://litellm.ai/)

**AI Command Center (acc)** is the elite orchestration environment for AI Agents and LLMs. It transforms your workstation into a professional-grade AI cockpit with **zero-effort** provisioning, real-time visual monitoring, and multi-tier hardware tuning.

Run any open-source model—from Phi-3 to Llama 3.1 70B—with a single command.

---

## 🌟 Vision: Zero Effort, Infinite Intelligence
acc is designed for the developer who wants to *use* AI, not fight with dependencies.
- **Master Orchestrator**: Just type `acc` to start.
- **Visual Dashboard**: Real-time TUI tracking CPU, RAM, and NVIDIA GPU metrics (Temp/Power).
- **Backend Agnostic**: Native support for **Ollama**, **LocalAI**, and **vLLM** via LiteLLM abstraction.
- **Private Fleet**: Separate your paid/private models (OpenAI, Anthropic, private fine-tunes) using the git-ignored `Config/private_fleet.conf`.
- **Elite Integration**: Seamlessly connect your existing agents, external engines, and cloud APIs.
- **Smart Provisioning**: Resilient multi-connection downloads (Aria2/HF-CLI) with automated reassembly.
- **Elite Tuning**: Automated kernel and driver optimization for maximum tokens-per-second.
- **Autonomous Intelligence**: Built-in "Architect" agents and interactive chat for instant reasoning.

---

## 🚀 Quick Start (Zero Effort)

```bash
# 1. Clone the cockpit
git clone https://github.com/intellibitz/acc.git
cd acc

# 2. Install & Link
./install.sh

# 3. Launch the visual dashboard
acc dash
```

---

## ⌨️ Dashboard Controls
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
- **Toolchain**: `docker`, `tmux`, `aria2c`, `jq`, `python3`.

---

## 🤝 Contributing
acc is built for the community. If you have ideas for better orchestration or new model superpowers, check out our [CONTRIBUTING.md](CONTRIBUTING.md).

## 🛡️ License
Distributed under the MIT License. See `LICENSE` for more information.

---
*Built with ❤️ for the AI Community. Unlock your hardware's true potential.*
