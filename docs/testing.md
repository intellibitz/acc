# Testing Strategy for Acc

Acc ensures reliability through automated protocol validation and environment auditing.

## 📡 Protocol Integrity Tests
Located in `:common:src:commonTest:kotlin:cc.thevar.acc.protocol.ProtocolTest`.
These tests ensure that the shared data models between the **Intelligence Layer** (Python), **Control Plane** (Kotlin Gateway), and **Deck** (Compose UI) are binary-compatible and serialization-stable.

To run:
```bash
./gradlew :common:jvmTest
```

## 🏗️ Hardware Audit
The `./acc setup` command runs an environment-native audit script that verifies:
- NVIDIA Driver versions.
- CUDA availability.
- Docker daemon status.
- Python dependency integrity.

## 🎨 Visual Parity
UI components are tested via Compose Previews and manual verification across:
- **WasmJs**: Browser-native dashboard.
- **JVM Desktop**: High-performance windowed app.
- **Android**: Mobile cockpit.

## 🛡️ Security Verification
The Gateway unit tests (planned) will verify that the `CommandHandler` white-list correctly blocks unauthorized commands.
Currently, this is verified manually by attempting non-whitelisted commands via the Console WebSocket.
