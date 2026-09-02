/**
 * GitHub Automation Tasks
 *
 * This script provides a set of repository management tasks using Git and GitHub CLI.
 * It is intentionally workflow-agnostic: it resolves the default branch from the repo,
 * accepts both main/master/trunk/develop conventions, and handles detached HEADs,
 * missing remotes, and local-only repositories gracefully.
 */

val gitHubCommonScript = """
    set -euo pipefail

    git rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
     echo "Not inside a git repository." >&2
     exit 1
    }

    # Helper: confirm action via env var or interactive prompt.
    # Usage: confirm_or_abort VAR_NAME "message"
    confirm_or_abort() {
      VAR_NAME="$1"; shift
      MSG="$*"
      VAL="${'$'}{!VAR_NAME:-}"
      VAL_LOWER="${'$'}(printf '%s' "${'$'}VAL" | tr '[:upper:]' '[:lower:]')"

      # Auto-confirm when explicitly set, when running in CI/GitHub Actions, or when AUTO_RESOLVE_MANUAL=true
      if [ "${'$'}VAL_LOWER" = "true" ] || [ "${'$'}(printf '%s' "${'$'}AUTO_RESOLVE_MANUAL" | tr '[:upper:]' '[:lower:]')" = "true" ] || [ -n "${'$'}GITHUB_ACTIONS" ] || [ -n "${'$'}CI" ]; then
        return 0
      fi

      # If running interactively, ask the user; otherwise require explicit env flag
      if [ -t 1 ]; then
        read -r -p "${'$'}MSG [y/N]: " ans
        case "${'$'}ans" in
          [yY]) return 0 ;;
          *) echo "Aborted by user."; exit 1 ;;
        esac
      else
        echo "${'$'}MSG - set ${'$'}VAR_NAME env var to true to confirm (non-interactive)" >&2
        exit 1
      fi
    }

    REMOTE_NAME="${'$'}(git remote | head -n 1 || true)"
    if [ -z "${'$'}REMOTE_NAME" ]; then
     echo "No git remote configured; operating in local-only mode."
    fi

    # Ensure gh is authenticated when present. Use GH_SKIP_AUTH_CHECK=true to bypass in CI or special cases.
    ensure_gh_authenticated() {
      if ! command -v gh >/dev/null 2>&1; then
        return 0
      fi
      # If GITHUB_TOKEN is present (CI/GitHub Actions), treat as authenticated
      if [ -n "${'$'}GITHUB_TOKEN" ]; then
        return 0
      fi
      if gh auth status >/dev/null 2>&1; then
        return 0
      fi
      echo "GitHub CLI 'gh' is installed but not authenticated. Run 'gh auth login' or set GH_SKIP_AUTH_CHECK=true to bypass." >&2
      # Allow auto-resolve in automation via AUTO_RESOLVE_MANUAL or explicit skip flag
      confirm_or_abort GH_SKIP_AUTH_CHECK "Proceed without gh authentication (remote operations may fail)?"
    }

    BASE_BRANCH=""
    if command -v gh >/dev/null 2>&1 && [ -n "${'$'}REMOTE_NAME" ]; then
     BASE_BRANCH="${'$'}(gh repo view --json defaultBranchRef -q .defaultBranchRef.name 2>/dev/null || true)"
    fi

    if [ -z "${'$'}BASE_BRANCH" ]; then
     for candidate in main master trunk develop; do
       if [ -n "${'$'}REMOTE_NAME" ] && git show-ref --verify --quiet "refs/remotes/${'$'}REMOTE_NAME/${'$'}candidate"; then
         BASE_BRANCH="${'$'}candidate"
         break
       fi
       if git show-ref --verify --quiet "refs/heads/${'$'}candidate"; then
         BASE_BRANCH="${'$'}candidate"
         break
       fi
     done
    fi

    if [ -z "${'$'}BASE_BRANCH" ]; then
     BASE_BRANCH="main"
    fi

    CURRENT_BRANCH="${'$'}(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo DETACHED)"
    if [ "${'$'}CURRENT_BRANCH" = "HEAD" ] || [ "${'$'}CURRENT_BRANCH" = "DETACHED" ]; then
     echo "Detached HEAD detected. Creating recovery branch from ${'$'}BASE_BRANCH..."
     git switch -c "fix/${'$'}(date +%Y%m%d-%H%M%S)-detached" "${'$'}BASE_BRANCH" 2>/dev/null || \
       git checkout -B "fix/${'$'}(date +%Y%m%d-%H%M%S)-detached" "${'$'}BASE_BRANCH"
     CURRENT_BRANCH="${'$'}(git rev-parse --abbrev-ref HEAD)"
    fi

    export REMOTE_NAME BASE_BRANCH CURRENT_BRANCH
""".trimIndent()

fun sanitizedBranchName(raw: String): String = raw.trim()
    .replace("[^A-Za-z0-9._/-]".toRegex(), "-")
    .replace("/{2,}".toRegex(), "/")
    .replace("^/|/$".toRegex(), "")
    .ifEmpty { "feature" }

val featureName = project.findProperty("featureName")?.toString()
    ?: project.findProperty("name")?.toString()
    ?: ""

val featureSuffix = if (featureName.isNotBlank()) "-${sanitizedBranchName(featureName)}" else ""

fun asGitHubScript(vararg parts: String): String = (listOf(gitHubCommonScript) + parts).joinToString("\n\n")

// High-level GitHub workflow tasks

tasks.register("github") {
    group = "github"
    description = "Runs the creator workflow: sync latest main, update the current branch, merge clean PRs, and report state."
    dependsOn("githubSync", "githubMergeAll", "githubStatus")
}


tasks.register("githubSync") {
    group = "github"
    description = "Smart sync (Kotlin-native)"
    dependsOn("githubSyncKotlin")
}

tasks.register("githubOpen") {
    group = "github"
    description = "Open repository web view (Kotlin-native)"
    dependsOn("githubSetupKotlin")
}

tasks.register("githubFeature") {
    group = "github"
    description = "Create feature branch (Kotlin-native)"
    dependsOn("githubSyncKotlin", "githubFeatureKotlin")
}

tasks.register("githubMain") {
    group = "github"
    description = "Return repo to default branch (Kotlin-native)"
    dependsOn("githubMainKotlin")
}

// Cleanup tasks: remote and local branch pruning

val pruneLocalDefault = project.findProperty("pruneLocalBranches")?.toString()?.equals("true", ignoreCase = true) ?: false
val deleteRemoteDefault = project.findProperty("deleteClosedPrBranches")?.toString()?.equals("true", ignoreCase = true) ?: false
// When true, remove obsolete branches automatically. Default TRUE for full automation.
val removeObsoleteDefault = project.findProperty("removeObsoleteBranches")?.toString()?.equals("true", ignoreCase = true) ?: true
// Number of days of inactivity after which a branch is considered obsolete (default: 90 days)
val obsoleteDays = project.findProperty("obsoleteDays")?.toString() ?: "90"

tasks.register("githubCleanupRemoteBranches") {
    group = "github"
    description = "Cleanup remote branches (Kotlin-native)"
    dependsOn("githubCleanupRemoteBranchesKotlin")
}

tasks.register("githubPruneLocalBranches") {
    group = "github"
    description = "Prune local branches (Kotlin-native)"
    dependsOn("githubPruneLocalBranchesKotlin")
}

tasks.register("githubPR") {
    group = "github"
    description = "Ensure PR exists (Kotlin-native)"
    dependsOn("githubSyncKotlin", "githubPRKotlin")
}

tasks.register("githubMerge") {
    group = "github"
    description = "Merge current branch PR (Kotlin-native)"
    dependsOn("githubPRKotlin", "githubMergeKotlin")
}

tasks.register("githubMergeAll") {
    group = "github"
    description = "Updates and enables auto-merge for all open pull requests across the repo (Kotlin-native)"
    dependsOn("githubSyncKotlin", "githubMergeAllKotlin")
}

val deleteClosedPrBranches = project.findProperty("deleteClosedPrBranches")?.toString()?.equals("true", ignoreCase = true) ?: false

tasks.register("githubCleanupClosedPRs") {
    group = "github"
    description = "Lists merged/closed PRs and deletes stale branch refs when requested (Kotlin-native)"
    doFirst {
       if (project.findProperty("deleteClosedPrBranches")?.toString()?.equals("true", ignoreCase = true) == true) {
          project.setProperty("REMOVE_MODE", "true")
       }
    }
    dependsOn("githubCleanupRemoteBranchesKotlin")
}

tasks.register("githubPRSummary") {
    group = "github"
    description = "Prints a safe PR summary (Kotlin-native)"
    dependsOn("githubPRSummaryKotlin")
}

tasks.register("githubSummary") {
    group = "github"
    description = "Alias for githubPRSummary."
    dependsOn("githubPRSummary")
}

tasks.register("githubFixAll") {
    group = "github"
    description = "Attempts to fix all open PRs by updating them and rerunning failed workflow runs (Kotlin-native)"
    dependsOn("githubSyncKotlin", "githubFixAllKotlin")
}

tasks.register("githubFixSecurity") {
    group = "github"
    description = "Checks repository security alerts and merges any security-related PRs when appropriate (Kotlin-native)."
    dependsOn("githubSyncKotlin", "githubFixSecurityKotlin")
}

tasks.register("githubSetup") {
    group = "github"
    description = "Optimizes the repository settings for GitHub automation workflows (Kotlin-native)."
    dependsOn("githubSetupKotlin")
}

// Pre-flight check for CI and local runs: verifies gh auth (unless skipped) and required env vars
tasks.register("githubPreflight") {
    group = "github"
    description = "Performs pre-flight checks for automation (Kotlin-native)."
    dependsOn("githubPreflightKotlin")
}

tasks.register("githubChecks") {
    group = "github"
    description = "Displays the status of GitHub Action checks for the current branch (Kotlin-native)."
    dependsOn("githubChecksKotlin")
}

tasks.register("githubIssues") {
    group = "github"
    description = "Lists all open issues for the project (Kotlin-native)."
    dependsOn("githubIssuesKotlin")
}

tasks.register("githubWiki") {
    group = "github"
    description = "Open the project Wiki (Kotlin-native placeholder)."
    dependsOn("githubWikiKotlin")
}

// New task: removes obsolete branches based on inactivity or merged/no-PR criteria.
tasks.register("githubRemoveObsoleteBranches") {
    group = "github"
    description = "Remove obsolete remote branches (Kotlin-native)"
    dependsOn("githubRemoveObsoleteBranchesKotlin")
}

// New task: removes local obsolete branches based on inactivity or merged criteria.
tasks.register("githubRemoveLocalObsoleteBranches") {
    group = "github"
    description = "Prune local obsolete branches (Kotlin-native)"
    dependsOn("githubRemoveLocalObsoleteBranchesKotlin")
}

tasks.register("githubActions") {
    group = "github"
    description = "Open GitHub Actions (Kotlin-native placeholder)"
    dependsOn("githubChecksKotlin")
}
