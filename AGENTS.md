# AI Agent Interaction Guide

Welcome to the **acc** codebase. This project uses a modern, modular architecture designed for high performance and testability.

## 🏗️ Architecture Overview
- **Dependency Injection**: We use **Koin** for both the Gateway and Frontend.
  - Gateway: Registered in `GatewayModule.kt`.
  - Frontend: Shared ViewModels in `CommonModule.kt`.
- **Control Plane (Gateway)**: A modular Ktor server.
  - `Routing.kt`: Modular route definitions.
  - `MonitoringService.kt`: Lifecycle-aware metrics and WebSocket management.
  - `ProvisioningService.kt`: Resource-safe model downloads (Ktor-native) and registration.
  - `AgentService.kt`: Kotlin-native integration with local and cloud LLMs.
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

## 🚀 Release Management
We use a semi-automated release system triggered by Gradle and completed by GitHub Actions.

### Release Tasks:
- **Test Release**: `./gradlew releaseTest`
  - Increments version (e.g., `1.0.0` -> `1.0.1-test.1` or `1.0.1-test.1` -> `1.0.1-test.2`).
  - Tags and pushes to GitHub.
  - Triggers a **Pre-release** on GitHub with a Fat JAR.
- **Production Release**: `./gradlew releaseProduction`
  - Promotes test to production or increments version (e.g., `1.0.1-test.2` -> `1.0.1` or `1.0.1` -> `1.0.2`).
  - Tags and pushes to GitHub.
  - Triggers a **Full Release** on GitHub.

### GitHub Creator Tasks:
Maintainers can use these specialized tasks to interact with the repository:
- **Open Repo**: `./gradlew githubOpen` (Opens browser)
- **Create PR**: `./gradlew githubPR` (Automates `--fill` PR creation)
- **Check Status**: `./gradlew githubChecks` (Watches CI check progress)
- **Manage Issues**: `./gradlew githubIssues` (Lists current issues)
- **View Actions**: `./gradlew githubActions` (Opens Actions tab)
- **Wiki**: `./gradlew githubWiki` (Opens Wiki)

### Deployment for Pilots:
Users can simply download the `acc-gateway-x.y.z.jar` from the GitHub Release and run:
`java -jar acc-gateway-x.y.z.jar`
The server and web frontend will be served at `http://localhost:8080`.

## 🛠️ Development Guidelines
- **Koin first**: Avoid global singletons. Inject dependencies via constructors or Koin DSL.
- **Lifecycle Management**: Core services (Supervisor, Provisioning, Monitoring) implement `AutoCloseable`. Always ensure background coroutines and processes are cancelled on `close()`.
- **Stay Modular**: When adding new features to the Gateway, create a new `Routes` file or Service instead of bloating `Application.kt`.
- **Structured Logging**: Use **SLF4J** (`LoggerFactory.getLogger(javaClass)`) instead of `println`.
- **Zero Silent Failures**: Never use empty `catch` blocks. Log errors at the appropriate level (WARN/ERROR).
- **Test-Driven**: Always add unit tests for new business logic.

---
*Maintained by the AI Command Center Architects.*
