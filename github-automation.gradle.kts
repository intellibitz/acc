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

    REMOTE_NAME="${'$'}(git remote | head -n 1 || true)"
    if [ -z "${'$'}REMOTE_NAME" ]; then
     echo "No git remote configured; operating in local-only mode."
    fi

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
    description = "Runs the standard GitHub automation workflow: sync, ensure a PR, and watch checks."
    dependsOn("githubSync", "githubPR", "githubChecks")
}

tasks.register<Exec>("githubStatus") {
    group = "github"
    description = "Displays the repo, current branch, default branch, and PR status when available."
    commandLine("bash", "-c", asGitHubScript(
        """
            echo "Repository: ${'$'}(git rev-parse --show-toplevel 2>/dev/null || pwd)"
            echo "Current branch: ${'$'}CURRENT_BRANCH"
            echo "Default branch: ${'$'}BASE_BRANCH"
            echo "------------------------------------------------------------"
            if command -v gh >/dev/null 2>&1; then
              gh pr status || gh repo view --json nameWithOwner
            else
              git status --short --branch
            fi
        """.trimIndent()
    ))
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

           if [ "${'$'}CURRENT_BRANCH" = "${'$'}BASE_BRANCH" ]; then
             if [ -n "${'$'}REMOTE_NAME" ]; then
               git fetch "${'$'}REMOTE_NAME" "${'$'}BASE_BRANCH" || true
               git reset --hard "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" 2>/dev/null || git reset --hard "${'$'}BASE_BRANCH"
             else
               git reset --hard "${'$'}BASE_BRANCH"
             fi
             exit 0
           fi

           if [ -n "${'$'}REMOTE_NAME" ] && git rev-parse --verify "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" >/dev/null 2>&1; then
             echo "🌿 Rebasing ${'$'}CURRENT_BRANCH onto ${'$'}REMOTE_NAME/${'$'}BASE_BRANCH..."
             git rebase "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH" || { echo "❌ Rebase onto ${'$'}BASE_BRANCH failed. Resolve conflicts manually." >&2; exit 1; }
           elif git show-ref --verify --quiet "refs/heads/${'$'}BASE_BRANCH"; then
             echo "🌿 Rebasing ${'$'}CURRENT_BRANCH onto local ${'$'}BASE_BRANCH..."
             git rebase "${'$'}BASE_BRANCH" || { echo "❌ Rebase onto ${'$'}BASE_BRANCH failed. Resolve conflicts manually." >&2; exit 1; }
           fi

           if [ -n "${'$'}REMOTE_NAME" ]; then
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
             git reset --hard "${'$'}REMOTE_NAME/${'$'}BASE_BRANCH"
           else
             git switch "${'$'}BASE_BRANCH" 2>/dev/null || git checkout "${'$'}BASE_BRANCH"
             git reset --hard "${'$'}BASE_BRANCH"
           fi
       """.trimIndent()
    ))
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
    description = "Updates and enables auto-merge for all open pull requests across the repo."
    dependsOn("githubSync")
    commandLine("bash", "-c", asGitHubScript(
       """
           if ! command -v gh >/dev/null 2>&1; then
             echo "GitHub CLI is not installed; skipping bulk merge."
             exit 0
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

tasks.register<Exec>("githubActions") {
    group = "github"
    description = "Opens the GitHub Actions tab in your default browser."
    commandLine("gh", "run", "list", "--web")
}
