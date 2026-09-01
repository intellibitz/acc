# AI Agent Interaction Guide

Welcome to the **acc** codebase. This project uses a modern, modular architecture designed for high performance and testability.

## 🏗️ Architecture Overview
- **Dependency Injection**: We use **Koin** for both the Gateway and Frontend.
  - Gateway: Registered in `GatewayModule.kt`.
  - Frontend: Shared ViewModels in `CommonModule.kt`.
- **Control Plane (Gateway)**: A modular Ktor server.
  - `Routing.kt`: Modular route definitions.
  - `MonitoringService.kt`: Lifecycle-aware metrics and WebSocket management.
  - `ProvisioningService.kt`: Resource-safe model downloads and registration.
- **Frontend (Deck)**: Compose Multiplatform.
  - Responsive layouts in `App.kt` with breakpoints for Desktop/Mobile.
  - **Platform-Aware Host Discovery**: The `Platform` interface provides default gateway hosts (e.g., `10.0.2.2` for Android emulators, `window.location.hostname` for Web).
  - Type-safe communication via shared `:common` protocols.

## 🧪 Testing Strategy
We follow a multi-layered testing approach. Detailed documentation can be found in [docs/testing.md](docs/testing.md).

### Key Test Suites:
- **Protocol Tests**: `:common:jvmTest`
- **Gateway Service Tests**: `:gateway:test` (uses MockK and Ktor MockEngine)
- **Frontend Build Verification**: `./gradlew :frontend:desktop:assemble`

## 🛠️ Development Guidelines
- **Koin first**: Avoid global singletons. Inject dependencies via constructors or Koin DSL.
- **Lifecycle Management**: Core services (Supervisor, Provisioning, Monitoring) implement `AutoCloseable`. Always ensure background coroutines and processes are cancelled on `close()`.
- **Stay Modular**: When adding new features to the Gateway, create a new `Routes` file or Service instead of bloating `Application.kt`.
- **Structured Logging**: Use **SLF4J** (`LoggerFactory.getLogger(javaClass)`) instead of `println`.
- **Zero Silent Failures**: Never use empty `catch` blocks. Log errors at the appropriate level (WARN/ERROR).
- **Test-Driven**: Always add unit tests for new business logic.

---
*Maintained by the AI Command Center Architects.*
