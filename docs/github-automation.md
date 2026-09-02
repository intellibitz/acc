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

- The automation attempts to update all feature branches (gh pr update-branch) before enabling auto-merge.

- GitHub CLI (gh) authentication:
  - The script checks for 'gh' being installed and (when present) verifies 'gh auth status'. If 'gh' is installed but not authenticated, the script will abort unless GH_SKIP_AUTH_CHECK=true is set in the environment.
  - For interactive local runs, the script will prompt to proceed without authentication (not recommended).
  - For CI, authenticate 'gh' via a GITHUB_TOKEN or use 'gh auth login --with-token' in the runner; alternatively, set GH_SKIP_AUTH_CHECK=true to bypass the check (not recommended).

- CI safety recommendations:
  - Non-interactive runs must explicitly allow destructive actions by setting MAIN_RESET_CONFIRM=true and FORCE_PUSH_CONFIRM=true.
  - Prefer authenticating 'gh' in CI instead of bypassing the auth check.

- The docs live in docs/github-automation.md; update as needed when behavior changes.
