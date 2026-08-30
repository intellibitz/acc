# 🚀 AI Command Center (ACC)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Linux](https://img.shields.io/badge/Platform-Linux%20%2F%20WSL2-blue.svg)](https://ubuntu.com/)
[![Engine: Backend-Agnostic](https://img.shields.io/badge/Engine-Ollama%20%7C%20LocalAI%20%7C%20vLLM-blueviolet.svg)](https://litellm.ai/)

**AI Command Center (ACC)** is the elite orchestration environment for AI Agents and LLMs. It transforms your workstation into a professional-grade AI cockpit with **zero-effort** provisioning, real-time visual monitoring, and multi-tier hardware tuning.

Run any open-source model—from Phi-3 to Llama 3.1 70B—with a single command.

---

## 🌟 Vision: Zero Effort, Infinite Intelligence
ACC is designed for the developer who wants to *use* AI, not fight with dependencies.
- **Master Orchestrator**: Just type `acc` to start.
- **Visual Dashboard**: Real-time TUI tracking CPU, RAM, and NVIDIA GPU metrics.
- **Backend Agnostic**: Native support for **Ollama**, **LocalAI**, and **vLLM** via LiteLLM abstraction.
- **Smart Provisioning**: Resilient multi-connection downloads (Aria2/HF-CLI) with automated reassembly.
- **Elite Tuning**: Automated kernel and driver optimization for maximum tokens-per-second.

---

## 🚀 Quick Start (Zero Effort)

```bash
# 1. Clone the cockpit
git clone https://github.com/your-username/acc.git
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
| **`S`** | **HF Search** | Discovery models directly from Hugging Face. |
| **`Y`** | **Proxy** | Start the LiteLLM gateway for OpenAI-compatible API access. |
| **`G`** | **Agent** | Launch the Autonomous Architect AI agent. |
| **`T`** | **Tune HW** | Apply low-level Linux kernel optimizations for AI. |
| **`M`** | **Method** | Toggle between HF-Transfer, Aria2, or Native Pull. |

---

## 🛠️ Prerequisites
- **OS**: Linux (Ubuntu/Debian) or WSL2.
- **Hardware**: NVIDIA GPU (Optional, but highly recommended).
- **Toolchain**: `docker`, `tmux`, `aria2c`, `jq`, `python3`.

---

## 🤝 Contributing
ACC is built for the community. If you have ideas for better orchestration or new model superpowers, check out our [CONTRIBUTING.md](CONTRIBUTING.md).

## 🛡️ License
Distributed under the MIT License. See `LICENSE` for more information.

---
*Built with ❤️ for the AI Community. Unlock your hardware's true potential.*
