# Upgrade Provisioning Logic from Bash to Kotlin (Option A)

This plan migrates the core heavy lifting from brittle Bash scripts (`provisioner.sh`, `osm.sh`) to a robust, type-safe Kotlin service within the Ktor Gateway.

## User Review Required

> [!IMPORTANT]
> The `fleet.conf` and `private_fleet.conf` bash-formatted files will be migrated to a single `fleet.json` for better integration with the Kotlin backend. The old files will be backed up.

## Proposed Changes

### 1. [Component] Common Protocol (Shared)
We need to define the data models for models and their provisioning status.

#### [MODIFY] [SystemProtocol.kt](file:///home/ramadoss/Projects/AI/acc/common/src/commonMain/kotlin/cc/thevar/acc/protocol/SystemProtocol.kt)
- Add `ModelManifest` to represent a model configuration.
- Add `ProvisioningUpdate` for real-time progress tracking (percentage, speed, stage).
- Update `SystemState` to include detailed provisioning info.

### 2. [Component] Gateway Service
Implement the engine that replaces the shell scripts.

#### [NEW] [ProvisioningService.kt](file:///home/ramadoss/Projects/AI/acc/gateway/src/main/kotlin/cc/thevar/acc/service/ProvisioningService.kt)
- Core logic for downloading (via `hf` or `aria2c` wrappers).
- Model registration in Ollama.
- Hardware-aware parameter calculation (moved from `calculate_gpu_layers`).
- State management for active tasks.

#### [NEW] [FleetManager.kt](file:///home/ramadoss/Projects/AI/acc/gateway/src/main/kotlin/cc/thevar/acc/service/FleetManager.kt)
- Handles migration and persistence of `fleet.json`.

#### [MODIFY] [Application.kt](file:///home/ramadoss/Projects/AI/acc/gateway/src/main/kotlin/cc/thevar/acc/Application.kt)
- Inject `ProvisioningService` and `FleetManager`.
- Add a new WebSocket `/ws/provisioning` for granular control.

### 3. [Component] Orchestrator (Bash)
The `./acc` script will be slimmed down to act as a light wrapper around the Gateway's new capabilities.

#### [MODIFY] [acc](file:///home/ramadoss/Projects/AI/acc/acc)
- Update `up`, `add`, `remove`, `prune` commands to call Gateway APIs instead of executing local scripts.

## Verification Plan

### Automated Tests
- Unit tests for `FleetManager` (JSON parsing/migration).
- Mocked `ProvisioningService` tests to verify state transitions.

### Manual Verification
- Trigger a model "Provision" from the UI or `./acc up`.
- Observe real-time progress bar in the UI.
- Verify model is correctly registered in Ollama with optimized parameters.
