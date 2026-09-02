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

CI workflow example

Below is an example GitHub Actions workflow (also committed to .github/workflows/github-automation.yml) that runs the preflight checks and optionally runs a Gradle automation task if provided via workflow_dispatch. Key points:

- The workflow runs ./gradlew githubPreflight first to verify gh authentication and the MAIN_RESET_CONFIRM/FORCE_PUSH_CONFIRM flags.
- For non-interactive CI runs set MAIN_RESET_CONFIRM and FORCE_PUSH_CONFIRM as repository secrets (or leave unset to prevent destructive changes).
- Prefer authenticating gh in CI using GITHUB_TOKEN rather than bypassing with GH_SKIP_AUTH_CHECK.

Snippet to include in other workflows (example step):

  - name: Run automation preflight
    run: ./gradlew githubPreflight
    env:
      MAIN_RESET_CONFIRM: ${{ secrets.MAIN_RESET_CONFIRM }}
      FORCE_PUSH_CONFIRM: ${{ secrets.FORCE_PUSH_CONFIRM }}
      GH_SKIP_AUTH_CHECK: ${{ secrets.GH_SKIP_AUTH_CHECK }}
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

Add this preflight step before any step that calls the actionable Gradle tasks (githubSync, githubMergeAll, githubMain, etc.).
E2E automation test at 20260902-203638

Rebase skip pattern

- REBASE_SKIP_PATTERN: Regex environment variable controlling which branches githubSync will skip rebasing. Default: '^(test/|automation/)'.
- Purpose: Prevent automated rebases for temporary or automation-managed branches (e.g., test/, automation/) so automation doesn't block on conflicts.
- Example (override): REBASE_SKIP_PATTERN='^(test/|automation/|wip/)' ./gradlew githubSync

Update the pattern in CI if you use different branch prefixes for temporary or automation branches.
