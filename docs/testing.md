# Testing Strategy for Acc

Acc ensures reliability through automated protocol validation and environment auditing.

## 📡 Protocol Integrity Tests
Located in `:common:src:commonTest:kotlin:cc.thevar.acc.protocol.ProtocolTest`.
These tests ensure that the shared data models between the **Intelligence Layer** (Python), **Control Plane** (Kotlin Gateway), and **Deck** (Compose UI) are binary-compatible and serialization-stable.

To run:
```bash
./gradlew :common:jvmTest
```

## 🐳 Container & Deployment Verification
With the pivot to a container-first architecture, ensure the production image builds correctly:

```bash
docker compose build acc-gateway
```

Verify the bridge's platform-aware logic:
```bash
# Within the container
python3 brain/system_bridge.py
```

## 🏗️ Hardware Audit
The `./acc setup` command performs a universal audit:
- **Docker/Podman** availability.
- **GPU Passthrough** capability (NVIDIA Container Toolkit or macOS virtualization).
- **Network Routing** between the Gateway and AI engines.

## 🎨 Visual Parity
UI components are tested via Compose Previews and manual verification across:
- **WasmJs**: Browser-native dashboard.
- **JVM Desktop**: High-performance windowed app.
- **Android**: Mobile cockpit.

## 🛡️ Security & Command Verification
The Gateway unit tests verify that the `CommandHandler` white-list correctly blocks unauthorized commands and routes valid ones.
Located in `:gateway:src:test:kotlin:cc.thevar.acc.service.CommandHandlerTest`.

To run:
```bash
./gradlew :gateway:test --tests "cc.thevar.acc.service.CommandHandlerTest"
```

## 🚀 Service & Provisioning Tests
Unit tests for core service logic, including hardware-aware layer calculation and fleet management.
Located in `:gateway:src:test:kotlin:cc.thevar.acc.service.ProvisioningServiceTest`.

To run:
```bash
./gradlew :gateway:test --tests "cc.thevar.acc.service.ProvisioningServiceTest"
```
