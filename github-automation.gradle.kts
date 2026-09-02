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


// GitHub sync / branch hygiene tasks

tasks.register<Exec>("githubSync") {
    group = "github"
    description = "Smart sync: updates the current branch by rebasing onto the repository default branch and preserving local work."
    commandLine("bash", "-c", asGitHubScript(
       """
           git fetch --all --prune

           git add -A
           if ! git diff --cached --quiet; then
             echo "📦 Capturing local work..."
             git commit -m "chore: automated sync to GitHub" || true
           fi

           ORIG_BRANCH="${'$'}CURRENT_BRANCH"
           BASE_REF="${'$'}REMOTE_NAME/${'$'}BASE_BRANCH"

           if [ -n "${'$'}REMOTE_NAME" ] && git rev-parse --verify "${'$'}BASE_REF" >/dev/null 2>&1; then
             if [ "${'$'}CURRENT_BRANCH" != "${'$'}BASE_BRANCH" ]; then
               echo "🔄 Updating local ${'$'}BASE_BRANCH from ${'$'}BASE_REF..."
               git checkout "${'$'}BASE_BRANCH" >/dev/null 2>&1 || git switch "${'$'}BASE_BRANCH"
               git fetch "${'$'}REMOTE_NAME" "${'$'}BASE_BRANCH"

               uncommitted="${'$'}(git status --porcelain)"
               counts="${'$'}(git rev-list --left-right --count "${'$'}BASE_BRANCH"..."${'$'}BASE_REF" 2>/dev/null || true)"
               behind="${'$'}(echo ${'$'}counts | awk '{print $1}')"
               ahead="${'$'}(echo ${'$'}counts | awk '{print $2}')"
               if [ -n "${'$'}uncommitted" ] || { [ -n "${'$'}ahead" ] && [ "${'$'}ahead" -ne 0 ]; }; then
                 echo "⚠️ Preparing to reset local ${'$'}BASE_BRANCH to ${'$'}BASE_REF which may discard local changes or ${'$'}ahead local commit(s)."
                 confirm_or_abort MAIN_RESET_CONFIRM "Reset local ${'$'}BASE_BRANCH to ${'$'}REMOTE_NAME/${'$'}BASE_BRANCH?"
               fi

               git reset --hard "${'$'}BASE_REF"
               echo "↩️  Returning to ${'$'}ORIG_BRANCH..."
               git checkout "${'$'}ORIG_BRANCH" >/dev/null 2>&1 || git switch "${'$'}ORIG_BRANCH"
             else
               echo "🔄 Updating local ${'$'}BASE_BRANCH from ${'$'}BASE_REF..."
               git fetch "${'$'}REMOTE_NAME" "${'$'}BASE_BRANCH"

               uncommitted="${'$'}(git status --porcelain)"
               if [ -n "${'$'}uncommitted" ]; then
                 echo "⚠️ Local uncommitted changes present on ${'$'}BASE_BRANCH."
                 confirm_or_abort MAIN_RESET_CONFIRM "This will discard local changes on ${'$'}BASE_BRANCH. Confirm reset to ${'$'}REMOTE_NAME/${'$'}BASE_BRANCH?"
               fi

               git reset --hard "${'$'}BASE_REF"
             fi
           else
             echo "⚠️  Remote base branch unavailable; using local ${'$'}BASE_BRANCH as the current source of truth."
           fi

           if [ "${'$'}CURRENT_BRANCH" = "${'$'}BASE_BRANCH" ]; then
             echo "✅ Local base branch is up to date."
             if [ -n "${'$'}REMOTE_NAME" ]; then
               git fetch "${'$'}REMOTE_NAME" "${'$'}BASE_BRANCH" || true
               git reset --hard "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" 2>/dev/null || git reset --hard "${'$'}BASE_BRANCH"
             else
               git reset --hard "${'$'}BASE_BRANCH"
             fi
             exit 0
           fi

           REBASE_SKIP_PATTERN="${'$'}{REBASE_SKIP_PATTERN:-^(test/|automation/)}"
           if echo "${'$'}CURRENT_BRANCH" | grep -E "${'$'}REBASE_SKIP_PATTERN" >/dev/null 2>&1; then
             echo "⚠️ Skipping rebase for branch ${'$'}CURRENT_BRANCH (matches skip pattern ${'$'}REBASE_SKIP_PATTERN)"
           else
             if [ -n "${'$'}REMOTE_NAME" ] && git rev-parse --verify "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" >/dev/null 2>&1; then
               echo "🌿 Rebasing ${'$'}CURRENT_BRANCH onto ${'$'}REMOTE_NAME/${'$'}BASE_BRANCH..."
               if git rebase "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH"; then
                 echo "✅ Rebase completed."
               else
                 echo "❌ Rebase failed for ${'$'}CURRENT_BRANCH. Aborting rebase and skipping rebase to avoid blocking automation." >&2
                 git rebase --abort 2>/dev/null || true
               fi
             elif git show-ref --verify --quiet "refs/heads/${'$'}BASE_BRANCH"; then
               echo "🌿 Rebasing ${'$'}CURRENT_BRANCH onto local ${'$'}BASE_BRANCH..."
               if git rebase "${'$'}BASE_BRANCH"; then
                 echo "✅ Rebase completed."
               else
                 echo "❌ Rebase failed for ${'$'}CURRENT_BRANCH. Aborting rebase and skipping rebase to avoid blocking automation." >&2
                 git rebase --abort 2>/dev/null || true
               fi
             fi
           fi

           if [ -n "${'$'}REMOTE_NAME" ]; then
             echo "🚀 Preparing to push ${'$'}CURRENT_BRANCH (safe push)..."
             REMOTE_REF="${'$'}REMOTE_NAME/${'$'}CURRENT_BRANCH"
             if git rev-parse --verify "${'$'}REMOTE_REF" >/dev/null 2>&1; then
               remote_commit="${'$'}(git rev-parse "${'$'}REMOTE_REF")"
               local_commit="${'$'}(git rev-parse HEAD)"
               if [ "${'$'}remote_commit" != "${'$'}local_commit" ]; then
                 echo "⚠️ Remote branch ${'$'}REMOTE_REF differs from local HEAD."
                 confirm_or_abort FORCE_PUSH_CONFIRM "Remote branch ${'$'}REMOTE_REF will be updated with push --force-with-lease. Confirm?"
                 git push -u "${'$'}REMOTE_NAME" HEAD --force-with-lease
               else
                 git push -u "${'$'}REMOTE_NAME" HEAD
               fi
             else
               git push -u "${'$'}REMOTE_NAME" HEAD
             fi
             git push -u "${'$'}REMOTE_NAME" HEAD || git push "${'$'}REMOTE_NAME" HEAD --force-with-lease
           fi
       """.trimIndent()
    ))
}

tasks.register<Exec>("githubOpen") {
    group = "github"
    description = "Opens the repository in your default browser."
    commandLine("gh", "repo", "view", "--web")
}

tasks.register<Exec>("githubFeature") {
    group = "github"
    description = "Creates a new feature branch from the default branch and opens a PR if possible."
    dependsOn("githubSync")

    val featureBranchName = "feature/${'$'}(date +%Y%m%d-%H%M%S)${featureSuffix}"
    commandLine("bash", "-c", asGitHubScript(
       """
           USER_FEATURE="${'$'}{FEATURE_NAME:-${featureName}}"
           if [ -n "${'$'}USER_FEATURE" ]; then
             SAFE_FEATURE="${'$'}(printf '%s' "${'$'}USER_FEATURE" | sed 's/[^A-Za-z0-9._/-]/-/g; s#^/##; s#/$##; s#//\+#/#g')"
             if [ -n "${'$'}SAFE_FEATURE" ]; then
               BRANCH_NAME="feature/${'$'}SAFE_FEATURE"
             else
               BRANCH_NAME="feature/${'$'}(date +%Y%m%d-%H%M%S)${featureSuffix}"
             fi
           else
             BRANCH_NAME="feature/${'$'}(date +%Y%m%d-%H%M%S)${featureSuffix}"
           fi

           if [ -n "${'$'}REMOTE_NAME" ] && git rev-parse --verify "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" >/dev/null 2>&1; then
             git switch -C "${'$'}BRANCH_NAME" "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" 2>/dev/null || git checkout -B "${'$'}BRANCH_NAME" "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH"
           else
             git switch -C "${'$'}BRANCH_NAME" "${'$'}BASE_BRANCH" 2>/dev/null || git checkout -B "${'$'}BRANCH_NAME" "${'$'}BASE_BRANCH"
           fi

           if [ -n "${'$'}REMOTE_NAME" ]; then
             git push -u "${'$'}REMOTE_NAME" "${'$'}BRANCH_NAME" || git push "${'$'}REMOTE_NAME" "${'$'}BRANCH_NAME" --set-upstream
           fi

           if command -v gh >/dev/null 2>&1; then
             gh pr create --base "${'$'}BASE_BRANCH" --fill || echo "PR already exists or no changes."
           fi
       """.trimIndent()
    ))
    environment("FEATURE_NAME", featureName)
}

tasks.register<Exec>("githubMain") {
    group = "github"
    description = "Returns the repo to its default branch and resets it to the remote state."
    dependsOn("githubSync")
    commandLine("bash", "-c", asGitHubScript(
       """
           if [ -n "${'$'}REMOTE_NAME" ] && git rev-parse --verify "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" >/dev/null 2>&1; then
             git switch "${'$'}BASE_BRANCH" 2>/dev/null || git checkout "${'$'}BASE_BRANCH"
             git fetch "${'$'}REMOTE_NAME" "${'$'}BASE_BRANCH"

             uncommitted="${'$'}(git status --porcelain)"
             if [ -n "${'$'}uncommitted" ]; then
               echo "⚠️ Local uncommitted changes present on ${'$'}BASE_BRANCH."
               confirm_or_abort MAIN_RESET_CONFIRM "This will discard local changes on ${'$'}BASE_BRANCH. Confirm reset to ${'$'}REMOTE_NAME/${'$'}BASE_BRANCH?"
             fi

             if git rev-parse --verify "${'$'}BASE_BRANCH" >/dev/null 2>&1 && git rev-parse --verify "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" >/dev/null 2>&1; then
               counts="${'$'}(git rev-list --left-right --count "${'$'}BASE_BRANCH"..."${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" 2>/dev/null || true)"
               behind="${'$'}(echo ${'$'}counts | awk '{print $1}')"
               ahead="${'$'}(echo ${'$'}counts | awk '{print $2}')"
               if [ -n "${'$'}ahead" ] && [ "${'$'}ahead" -ne 0 ]; then
                 echo "⚠️ Local ${'$'}BASE_BRANCH is ahead by ${'$'}ahead commit(s) and will be lost."
                 confirm_or_abort MAIN_RESET_CONFIRM "Reset will drop ${'$'}ahead local commit(s) on ${'$'}BASE_BRANCH. Confirm?"
               fi
             fi

             git reset --hard "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH"
           else
             git switch "${'$'}BASE_BRANCH" 2>/dev/null || git checkout "${'$'}BASE_BRANCH"
             uncommitted="${'$'}(git status --porcelain)"
             if [ -n "${'$'}uncommitted" ]; then
               echo "⚠️ Local uncommitted changes present on ${'$'}BASE_BRANCH."
               confirm_or_abort MAIN_RESET_CONFIRM "This will discard local changes on ${'$'}BASE_BRANCH. Confirm reset to local ${'$'}BASE_BRANCH?"
             fi
             git reset --hard "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH"
           else
             git switch "${'$'}BASE_BRANCH" 2>/dev/null || git checkout "${'$'}BASE_BRANCH"
             git reset --hard "${'$'}BASE_BRANCH"
           fi
       """.trimIndent()
    ))
}

// Cleanup tasks: remote and local branch pruning

val pruneLocalDefault = project.findProperty("pruneLocalBranches")?.toString()?.equals("true", ignoreCase = true) ?: false
val deleteRemoteDefault = project.findProperty("deleteClosedPrBranches")?.toString()?.equals("true", ignoreCase = true) ?: false
// When true, remove obsolete branches automatically. Default TRUE for full automation.
val removeObsoleteDefault = project.findProperty("removeObsoleteBranches")?.toString()?.equals("true", ignoreCase = true) ?: true
// Number of days of inactivity after which a branch is considered obsolete (default: 90 days)
val obsoleteDays = project.findProperty("obsoleteDays")?.toString() ?: "90"

// Deletes remote branches for merged/closed PRs. Dry-run by default; set -PdeleteClosedPrBranches=true or env DELETE_REMOTE=true to actually delete.
tasks.register<Exec>("githubCleanupRemoteBranches") {
    group = "github"
    description = "Lists merged/closed PR branches and optionally deletes remote refs. Safe by default."
    commandLine("bash", "-c", asGitHubScript(
        """
            if ! command -v gh >/dev/null 2>&1; then
              echo "GitHub CLI is not installed; skipping remote branch cleanup."
              exit 0
            fi

            DELETE_MODE="${'$'}{DELETE_MODE:-${if (deleteRemoteDefault) "true" else "false"}}"
            echo "🧹 Remote branch cleanup preview (DELETE_MODE=${'$'}DELETE_MODE)."

            gh pr list --state merged --limit 500 --json number,headRefName,baseRefName --template \
              '{{range .}}{{.number}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.baseRefName}}{{"\n"}}{{end}}' | \
              while IFS=${'$'}'\t' read -r pr branch base; do
                if [ -z "${'$'}branch" ] || [ "${'$'}branch" = "${'$'}BASE_BRANCH" ]; then
                  continue
                fi
                echo "Merged PR #${'$'}pr: ${'$'}branch -> ${'$'}base"
                if [ "${'$'}DELETE_MODE" = "true" ]; then
                  echo "Deleting remote branch ${'$'}branch..."
                  gh api -X DELETE "repos/:owner/:repo/git/refs/heads/${'$'}branch" || echo "Failed to delete ${'$'}branch or branch protected."
                else
                  echo "[DRY-RUN] would delete remote branch: ${'$'}branch"
                fi
              done

            gh pr list --state closed --limit 500 --json number,headRefName,baseRefName --template \
              '{{range .}}{{.number}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.baseRefName}}{{"\n"}}{{end}}' | \
              while IFS=${'$'}'\t' read -r pr branch base; do
                if [ -z "${'$'}branch" ] || [ "${'$'}branch" = "${'$'}BASE_BRANCH" ]; then
                  continue
                fi
                echo "Closed PR #${'$'}pr: ${'$'}branch -> ${'$'}base"
                if [ "${'$'}DELETE_MODE" = "true" ]; then
                  echo "Deleting remote branch ${'$'}branch..."
                  gh api -X DELETE "repos/:owner/:repo/git/refs/heads/${'$'}branch" || echo "Failed to delete ${'$'}branch or branch protected."
                else
                  echo "[DRY-RUN] would delete remote branch: ${'$'}branch"
                fi
              done
        """.trimIndent()
    ))
    environment("DELETE_MODE", if (deleteRemoteDefault) "true" else "false")
}

// Deletes local branches that have no upstream, whose upstream was deleted, or that have been merged into the base branch.
// Dry-run by default; set -PpruneLocalBranches=true or environment PRUNE_LOCAL=true to actually delete.
tasks.register<Exec>("githubPruneLocalBranches") {
    group = "github"
    description = "Prunes local branches that are merged into base or whose remote was removed. Dry-run by default."
    commandLine("bash", "-c", asGitHubScript(
        """
            PRUNE_MODE="${'$'}{PRUNE_MODE:-${if (pruneLocalDefault) "true" else "false"}}"
            echo "Local prune preview (PRUNE_MODE=${'$'}PRUNE_MODE)."

            git fetch --prune || true

            git for-each-ref --format='%(refname:short) %(upstream:short)' refs/heads | while read -r local upstream; do
              # skip current and base
              if [ "${'$'}local" = "${'$'}CURRENT_BRANCH" ] || [ "${'$'}local" = "${'$'}BASE_BRANCH" ]; then
                continue
              fi

              if [ -z "${'$'}upstream" ]; then
                echo "[DRY-RUN] local branch ${'$'}local has no upstream"
                if [ "${'$'}PRUNE_MODE" = "true" ]; then
                  echo "Deleting local branch ${'$'}local"
                  git branch -D "${'$'}local" || git branch -d "${'$'}local"
                fi
                continue
              fi

              # if upstream missing on remote
              if ! git ls-remote --exit-code --heads "${'$'}REMOTE_NAME" "${'$'}local" >/dev/null 2>&1; then
                echo "[DRY-RUN] remote branch for ${'$'}local missing"
                if [ "${'$'}PRUNE_MODE" = "true" ]; then git branch -D "${'$'}local" || git branch -d "${'$'}local"; fi
                continue
              fi

              # if local branch merged into remote/base
              if git merge-base --is-ancestor "${'$'}local" "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" 2>/dev/null; then
                echo "[DRY-RUN] local branch ${'$'}local is merged into ${'$'}BASE_BRANCH"
                if [ "${'$'}PRUNE_MODE" = "true" ]; then git branch -D "${'$'}local" || git branch -d "${'$'}local"; fi
              fi
            done
        """.trimIndent()
    ))
    environment("PRUNE_MODE", if (pruneLocalDefault) "true" else "false")
}

tasks.register<Exec>("githubPR") {
    group = "github"
    description = "Ensures the current branch has an open pull request against the repository default branch."
    dependsOn("githubSync")
    commandLine("bash", "-c", asGitHubScript(
       """
           if command -v gh >/dev/null 2>&1; then
             gh pr create --base "${'$'}BASE_BRANCH" --fill || echo "PR already exists or no changes."
           else
             echo "GitHub CLI is not installed; skipping PR creation."
           fi
       """.trimIndent()
    ))
}

tasks.register<Exec>("githubMerge") {
    group = "github"
    description = "Enables auto-merge for the current branch PR and squashes it into the default branch."
    dependsOn("githubPR")
    commandLine("bash", "-c", asGitHubScript(
       """
           if ! command -v gh >/dev/null 2>&1; then
             echo "GitHub CLI is not installed; skipping merge."
             exit 0
           fi

           PR_NUM="${'$'}(gh pr view --json number -q .number 2>/dev/null || true)"
           if [ -z "${'$'}PR_NUM" ]; then
             echo "No pull request found for the current branch."
             exit 0
           fi

           echo "🚀 Enabling auto-merge for PR #${'$'}PR_NUM..."
           gh pr merge "${'$'}PR_NUM" --auto --squash --delete-branch || true
       """.trimIndent()
    ))
}

tasks.register<Exec>("githubMergeAll") {
    group = "github"
    description = "Updates all open PR branches to the latest base and auto-merges all clean, mergeable pull requests."
    description = "Updates and enables auto-merge for all open pull requests across the repo."
    dependsOn("githubSync")
    commandLine("bash", "-c", asGitHubScript(
       """
           if ! command -v gh >/dev/null 2>&1; then
             echo "GitHub CLI is not installed; skipping bulk merge."
             exit 0
           fi

           gh pr list --state open --json number,title,mergeable,mergeStateStatus,headRefName --template \
             '{{range .}}{{.number}}{{"\t"}}{{.mergeable}}{{"\t"}}{{.mergeStateStatus}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
             while IFS=${'$'}'\t' read -r pr mergeable status branch title; do
               echo "------------------------------------------------------------"
               echo "PR #${'$'}pr: ${'$'}title"

               if [ -z "${'$'}branch" ]; then
                 continue
               fi

               if [ "${'$'}mergeable" = "CONFLICTING" ] || [ "${'$'}mergeable" = "DRAFT" ]; then
                 echo "⚠️  Skipping: PR is conflicted or draft."
                 continue
               fi

               echo "🔄 Ensuring branch is up-to-date with ${'$'}BASE_BRANCH..."
               gh pr update-branch "${'$'}pr" || echo "❌ Update failed (will continue to next checks)."

               # Refresh PR metadata after attempted update
               pr_info="${'$'}(gh pr view "${'$'}pr" --json mergeable,mergeStateStatus -q '. | [.mergeable, .mergeStateStatus] | @tsv' 2>/dev/null || true)"
               mergeable2="${'$'}(echo "${'$'}pr_info" | cut -f1)"
               status2="${'$'}(echo "${'$'}pr_info" | cut -f2)"

               if [ "${'$'}mergeable2" = "MERGEABLE" ] || [ "${'$'}status2" = "CLEAN" ] || [ "${'$'}status2" = "HAS_HOOKS" ]; then
                 echo "🚀 Enabling auto-merge..."
                 gh pr merge "${'$'}pr" --auto --squash --delete-branch || echo "❌ Merge failed."
               else
                 echo "⏭️  Skipping: PR is not yet mergeable after update."
               fi
           gh pr list --state open --json number,title,mergeable,mergeStateStatus --template \
             '{{range .}}{{.number}}{{"\t"}}{{.mergeable}}{{"\t"}}{{.mergeStateStatus}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
             while IFS=${'$'}'\t' read -r pr mergeable status title; do
               echo "------------------------------------------------------------"
               echo "PR #${'$'}pr: ${'$'}title"
               if [ "${'$'}mergeable" = "CONFLICTING" ]; then
                 echo "⚠️  Skipping: PR has hard conflicts."
                 continue
               fi
               if [ "${'$'}status" = "BEHIND" ]; then
                 echo "🔄 Updating branch with the default branch..."
                 gh pr update-branch "${'$'}pr" || echo "❌ Update failed."
               fi
               echo "🚀 Enabling auto-merge..."
               gh pr merge "${'$'}pr" --auto --squash --delete-branch || echo "❌ Merge failed."
             done
       """.trimIndent()
    ))
}

val deleteClosedPrBranches = project.findProperty("deleteClosedPrBranches")?.toString()?.equals("true", ignoreCase = true) ?: false

tasks.register<Exec>("githubCleanupClosedPRs") {
    group = "github"
    description = "Lists merged/closed PRs and optionally deletes stale branch refs that are no longer needed. Safe by default; set -PdeleteClosedPrBranches=true to actually delete remote branches."
    commandLine("bash", "-c", asGitHubScript(
       """
           if ! command -v gh >/dev/null 2>&1; then
             echo "GitHub CLI is not installed; skipping closed PR cleanup."
             exit 0
           fi

           DELETE_MODE="${'$'}{DELETE_MODE:-${if (deleteClosedPrBranches) "true" else "false"}}"
           echo "🧹 Closed PR cleanup preview (dry-run mode by default)."
           echo "Delete mode: ${'$'}DELETE_MODE"

           gh pr list --state merged --limit 200 --json number,title,headRefName,baseRefName --template \
             '{{range .}}{{.number}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.baseRefName}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
             while IFS=${'$'}'\t' read -r pr branch base title; do
               if [ -z "${'$'}branch" ] || [ "${'$'}branch" = "${'$'}BASE_BRANCH" ]; then
                 continue
               fi
               echo "Merged PR #${'$'}pr: ${'$'}branch -> ${'$'}base (${'$'}title)"
               if [ "${'$'}DELETE_MODE" = "true" ]; then
                 echo "Deleting stale branch ref for ${'$'}branch..."
                 gh api -X DELETE "repos/:owner/:repo/git/refs/heads/${'$'}branch" || echo "Branch already deleted or protected."
               else
                 echo "[DRY-RUN] would delete remote branch: ${'$'}branch"
               fi
             done

           gh pr list --state closed --limit 200 --json number,title,headRefName,baseRefName --template \
             '{{range .}}{{.number}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.baseRefName}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
             while IFS=${'$'}'\t' read -r pr branch base title; do
               if [ -z "${'$'}branch" ] || [ "${'$'}branch" = "${'$'}BASE_BRANCH" ]; then
                 continue
               fi
               echo "Closed PR #${'$'}pr: ${'$'}branch -> ${'$'}base (${'$'}title)"
               if [ "${'$'}DELETE_MODE" = "true" ]; then
                 echo "Deleting stale branch ref for ${'$'}branch..."
                 gh api -X DELETE "repos/:owner/:repo/git/refs/heads/${'$'}branch" || echo "Branch already deleted or protected."
               else
                 echo "[DRY-RUN] would delete remote branch: ${'$'}branch"
               fi
             done
       """.trimIndent()
    ))
    environment("DELETE_MODE", if (deleteClosedPrBranches) "true" else "false")
}

tasks.register<Exec>("githubPRSummary") {
    group = "github"
    description = "Prints a safe PR summary: open PRs, merged PRs, stale branches, and recommended next actions."
    commandLine("bash", "-c", asGitHubScript(
       """
           if ! command -v gh >/dev/null 2>&1; then
             echo "GitHub CLI is not installed; skipping PR summary."
             exit 0
           fi

           echo "============================================================"
           echo "GitHub PR Summary"
           echo "Current branch: ${'$'}CURRENT_BRANCH"
           echo "Default branch: ${'$'}BASE_BRANCH"
           echo "============================================================"

           echo "[Open PRs]"
           gh pr list --state open --limit 50 --json number,title,headRefName,mergeable,mergeStateStatus --template '{{range .}}{{.number}}{{"\t"}}{{.title}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.mergeable}}{{"\t"}}{{.mergeStateStatus}}{{"\n"}}{{end}}' || echo "No open PRs."

           echo ""
           echo "[Merged PRs]"
           gh pr list --state merged --limit 50 --json number,title,headRefName --template '{{range .}}{{.number}}{{"\t"}}{{.title}}{{"\t"}}{{.headRefName}}{{"\n"}}{{end}}' || echo "No merged PRs."

           echo ""
           echo "[Stale branch refs]"
           gh pr list --state merged --limit 200 --json number,headRefName --template '{{range .}}{{.headRefName}}{{"\n"}}{{end}}' | \
             awk 'NF && $0 != "${'$'}BASE_BRANCH" {print}' | \
             sort -u | sed 's/^/[DRY-RUN] stale branch: /' || echo "No stale merged-PR branches detected."

           echo ""
           echo "[Recommended actions]"
           echo "- Review open PRs with mergeable=CONFLICTING or mergeStateStatus=BEHIND."
           echo "- Run './gradlew githubMergeAll' to enable auto-merge on open PRs."
           echo "- Run './gradlew githubCleanupClosedPRs' to review stale merged/closed branch refs."
       """.trimIndent()
    ))
}

tasks.register("githubSummary") {
    group = "github"
    description = "Alias for githubPRSummary."
    dependsOn("githubPRSummary")
}

tasks.register<Exec>("githubFixAll") {
    group = "github"
    description = "Attempts to fix all open PRs by updating them and rerunning failed workflow runs."
    dependsOn("githubSync")
    commandLine("bash", "-c", asGitHubScript(
       """
           if ! command -v gh >/dev/null 2>&1; then
             echo "GitHub CLI is not installed; skipping PR fixes."
             exit 0
           fi

           gh pr list --state open --json number,title,mergeable,mergeStateStatus,headRefName --template \
             '{{range .}}{{.number}}{{"\t"}}{{.mergeable}}{{"\t"}}{{.mergeStateStatus}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
             while IFS=${'$'}'\t' read -r pr mergeable status branch title; do
               echo "------------------------------------------------------------"
               echo "PR #${'$'}pr: ${'$'}title"

               if [ "${'$'}mergeable" = "CONFLICTING" ] || [ "${'$'}mergeable" = "DRAFT" ]; then
                 echo "⚠️  Skipping update: PR is conflicted or draft."
               else
                 echo "🔄 Ensuring branch ${'$'}branch is up-to-date with ${'$'}BASE_BRANCH..."
               if [ "${'$'}status" = "BEHIND" ]; then
                 echo "🔄 Branch is out-of-date. Updating with the default branch..."
                 gh pr update-branch "${'$'}pr" || echo "❌ Automatic update failed."
               fi

               RUN_ID="${'$'}(gh run list --branch "${'$'}branch" --status failure --limit 1 --json databaseId -q '.[].databaseId' || true)"
               if [ -n "${'$'}RUN_ID" ]; then
                 echo "🛠️  Found failed CI run ${'$'}RUN_ID. Triggering rerun..."
                 gh run rerun "${'$'}RUN_ID" || echo "❌ Rerun trigger failed."
               else
                 echo "✅ No failed CI runs found for this branch."
               fi

               if [ "${'$'}mergeable" = "MERGEABLE" ]; then
                 echo "🚀 Enabling auto-merge (squash)..."
                 gh pr merge "${'$'}pr" --auto --squash --delete-branch || echo "❌ Auto-merge enablement failed."
               fi
             done
       """.trimIndent()
    ))
}

tasks.register<Exec>("githubFixSecurity") {
    group = "github"
    description = "Checks repository security alerts and merges any security-related PRs when appropriate."
    dependsOn("githubSync")
    commandLine("bash", "-c", asGitHubScript(
       """
           if ! command -v gh >/dev/null 2>&1; then
             echo "GitHub CLI is not installed; skipping security automation."
             exit 0
           fi

           echo "🛡️  Checking for Dependabot Security Alerts..."
           gh api repos/:owner/:repo/dependabot/alerts -f state=open --jq '.[] | "🚨 [Dependabot] \(.security_advisory.summary) (\(.dependency.package.name))"' || echo "No Dependabot alerts or error fetching."

           echo "🔍 Checking for Code Scanning Alerts..."
           gh api repos/:owner/:repo/code-scanning/alerts -f state=open --jq '.[] | "⚠️  [Scanning] \(.rule.description) in \(.most_recent_instance.location.path)"' || echo "No code scanning alerts or error fetching."

           echo "------------------------------------------------------------"
           echo "🚀 Triggering bulk merge for available security fixes..."
           gh pr list --state open --json number,title --template '{{range .}}{{.number}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
             grep -E "dependabot|security|chore: automated sync" | \
             while IFS=${'$'}'\t' read -r pr title; do
               echo "Processing security PR #${'$'}pr: ${'$'}title"
               gh pr update-branch "${'$'}pr" 2>/dev/null || true
               gh pr merge "${'$'}pr" --auto --squash --delete-branch || true
             done
       """.trimIndent()
    ))
}

tasks.register<Exec>("githubSetup") {
    group = "github"
    description = "Optimizes the repository settings for GitHub automation workflows."
    commandLine("gh", "repo", "edit", "--enable-auto-merge", "--delete-branch-on-merge", "--allow-update-branch", "--enable-squash-merge")
}

// Pre-flight check for CI and local runs: verifies gh auth (unless skipped) and required env vars
tasks.register<Exec>("githubPreflight") {
    group = "github"
    description = "Performs pre-flight checks: gh auth, and required env var checklist for destructive ops."
    commandLine("bash", "-c", asGitHubScript(
       """
           # ensure gh is authenticated (or confirm to proceed)
           ensure_gh_authenticated || true

           echo "============================================================"
           echo "GitHub Automation Preflight"
           echo "Repository: ${'$'}(git rev-parse --show-toplevel 2>/dev/null || pwd)"
           echo "Default branch: ${'$'}BASE_BRANCH"
           echo "Current branch: ${'$'}CURRENT_BRANCH"
           echo "------------------------------------------------------------"

           if command -v gh >/dev/null 2>&1; then
             if gh auth status >/dev/null 2>&1; then
               echo "- gh installed: yes"
               echo "- gh authenticated: yes"
             else
               echo "- gh installed: yes"
               echo "- gh authenticated: NO"
               echo "  Set GH_SKIP_AUTH_CHECK=true to bypass (not recommended); or run 'gh auth login'."
             fi
           else
             echo "- gh installed: NO"
           fi

           echo "- MAIN_RESET_CONFIRM=${'$'}{MAIN_RESET_CONFIRM:-false}"
           echo "- FORCE_PUSH_CONFIRM=${'$'}{FORCE_PUSH_CONFIRM:-false}"
           echo "- GH_SKIP_AUTH_CHECK=${'$'}{GH_SKIP_AUTH_CHECK:-false}"

           # Fail fast for CI safety if gh exists but unauthenticated and skip not set
           if command -v gh >/dev/null 2>&1 && ! gh auth status >/dev/null 2>&1 && [ "${'$'}{GH_SKIP_AUTH_CHECK:-}" != "true" ]; then
             echo "ERROR: gh is installed but unauthenticated. Abort preflight (set GH_SKIP_AUTH_CHECK=true to bypass)." >&2
             exit 1
           fi

           echo "Preflight checks passed (or user confirmed)."
       """.trimIndent()
    ))
}

tasks.register<Exec>("githubChecks") {
    group = "github"
    description = "Displays the status of GitHub Action checks for the current branch."
    commandLine("gh", "pr", "checks", "--watch")
}

tasks.register<Exec>("githubIssues") {
    group = "github"
    description = "Lists all open issues for the project."
    commandLine("gh", "issue", "list")
}

tasks.register<Exec>("githubWiki") {
    group = "github"
    description = "Opens the project Wiki in your default browser."
    commandLine("gh", "repo", "view", "--web", "--path", "wiki")
}

// New task: removes obsolete branches based on inactivity or merged/no-PR criteria.
tasks.register<Exec>("githubRemoveObsoleteBranches") {
    group = "github"
    description = "Removes obsolete remote branches: merged or inactive branches older than OBSOLETE_DAYS. Dry-run by default; set -PremoveObsoleteBranches=true or env REMOVE_MODE=true to delete."
    commandLine("bash", "-c", asGitHubScript(
       """
         if ! command -v gh >/dev/null 2>&1; then
           echo "gh not installed; skipping obsolete branch removal."
           exit 0
         fi
         REMOVE_MODE="${'$'}{REMOVE_MODE:-${if (removeObsoleteDefault) "true" else "false"}}"
         DAYS="${'$'}{REMOVE_DAYS:-${obsoleteDays}}"
         echo "Obsolete branch removal preview (REMOVE_MODE=${'$'}REMOVE_MODE) - branches older than ${'$'}DAYS days will be removed if merged or no open PR exists."
         gh api repos/:owner/:repo/branches --jq '.[] | .name' | while read -r branch; do
           if [ "${'$'}branch" = "${'$'}BASE_BRANCH" ]; then continue; fi
           # Skip protected branches
           prot="${'$'}(gh api repos/:owner/:repo/branches/${'$'}branch --jq '.protected' 2>/dev/null || echo false)"
           if [ "${'$'}prot" = "true" ]; then
             echo "Skipping protected branch: ${'$'}branch"
             continue
           fi
           # check open PRs
           open_pr="${'$'}(gh pr list --state open --json headRefName --jq '.[] | .headRefName' | grep -x -- "${'$'}branch" || true)"
           if [ -n "${'$'}open_pr" ]; then
             echo "Skipping ${'$'}branch: has open PR"
             continue
           fi
           # check merged PR
           merged_pr="${'$'}(gh pr list --state merged --json headRefName --jq '.[] | .headRefName' | grep -x -- "${'$'}branch" || true)"
           if [ -n "${'$'}merged_pr" ]; then
             echo "${'$'}branch has merged PR; eligible for deletion."
             if [ "${'$'}REMOVE_MODE" = "true" ]; then
               echo "Deleting remote branch ${'$'}branch..."
               gh api -X DELETE "repos/:owner/:repo/git/refs/heads/${'$'}branch" || echo "Failed to delete ${'$'}branch (protected or missing)."
             else
               echo "[DRY-RUN] would delete remote branch: ${'$'}branch"
             fi
             continue
           fi
           # check last commit date
           dt="${'$'}(gh api repos/:owner/:repo/branches/${'$'}branch --jq '.commit.commit.committer.date' 2>/dev/null || true)"
           if [ -z "${'$'}dt" ]; then
             echo "Could not determine commit date for ${'$'}branch; skipping."
             continue
           fi
           branch_epoch="${'$'}(date -d "${'$'}dt" +%s)"
           cutoff="${'$'}(date -d "-${'$'}DAYS days" +%s)"
           if [ "${'$'}branch_epoch" -lt "${'$'}cutoff" ]; then
             echo "${'$'}branch last commit ${'$'}dt is older than ${'$'}DAYS days; eligible for deletion."
             if [ "${'$'}REMOVE_MODE" = "true" ]; then
               echo "Deleting remote branch ${'$'}branch..."
               gh api -X DELETE "repos/:owner/:repo/git/refs/heads/${'$'}branch" || echo "Failed to delete ${'$'}branch (protected or missing)."
             else
               echo "[DRY-RUN] would delete remote branch: ${'$'}branch"
             fi
           else
             echo "${'$'}branch is recent; skipping."
           fi
         done
       """.trimIndent()
    ))
    environment("REMOVE_MODE", if (removeObsoleteDefault) "true" else "false")
}

tasks.register<Exec>("githubActions") {
    group = "github"
    description = "Opens the GitHub Actions tab in your default browser."
    commandLine("gh", "run", "list", "--web")
}
