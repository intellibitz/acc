GitHub Automation: safety flags and behavior

This project includes a Gradle-based GitHub automation script (github-automation.gradle.kts) that performs common repository maintenance tasks.

New safety features

- confirm_or_abort helper: Requires explicit confirmation for destructive operations. When running interactively, the script will prompt for confirmation.

- MAIN_RESET_CONFIRM: When set to "true" (environment variable), allows the script to perform destructive resets of the default branch (e.g., git reset --hard). Without this, the script will abort when a reset would discard local changes or drop local commits.

- FORCE_PUSH_CONFIRM: When set to "true" (environment variable), permits force-with-lease pushes that overwrite a remote branch. Otherwise, the script refuses to force-push and will prompt for confirmation in interactive runs.

Usage examples

- Interactive local run (prompts):
  ./gradlew githubSync

- Non-interactive CI run (explicit confirmation via env):
  MAIN_RESET_CONFIRM=true FORCE_PUSH_CONFIRM=true ./gradlew githubSync

Notes

- The automation attempts to update all feature branches (gh pr update-branch) before enabling auto-merge. Ensure the GitHub CLI (gh) is installed and authenticated for operations that touch remote PRs.
- The docs live in docs/github-automation.md; update as needed when behavior changes.
