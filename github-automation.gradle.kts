/**
 * GitHub Automation Tasks
 * 
 * This script provides a set of "Smart Sync" and repository management tasks
 * using the Git and GitHub (`gh`) CLIs. It can be applied to any Gradle project.
 * 
 * Usage:
 * apply(from = "github-automation.gradle.kts")
 */

tasks.register<Exec>("githubSync") {
    group = "github"
    description = "Smart sync: Keeps current branch updated with remote and origin/main."
    val script = """
        git fetch --all --prune
        CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
        
        # Capture local work
        git add .
        if ! git diff --cached --quiet; then
          echo "📦 Capturing local work..."
          git commit -m 'chore: automated sync to GitHub' || true
        fi

        if [ "${'$'}CURRENT_BRANCH" = "main" ]; then
          if ! git diff --quiet origin/main; then
            SYNC_BRANCH="sync/${'$'}(date +%Y%m%d-%H%M%S)"
            echo "⚠️  Dirty main branch detected. Moving work to ${'$'}SYNC_BRANCH..."
            git checkout -b "${'$'}SYNC_BRANCH"
            git push -u origin "${'$'}SYNC_BRANCH"
            if command -v gh >/dev/null 2>&1; then
              gh pr create --fill || echo "PR exists or no changes."
            fi
            git checkout main
          fi
          echo "🔄 Resetting main to origin/main..."
          git reset --hard origin/main
        else
          echo "🌿 On feature branch: ${'$'}CURRENT_BRANCH"
          echo "1. Pulling remote updates..."
          git pull --rebase origin "${'$'}CURRENT_BRANCH" || { echo "❌ Pull failed."; exit 1; }
          
          echo "2. Rebasing on origin/main..."
          git rebase origin/main || { echo "❌ Rebase on main failed. Resolve conflicts manually."; exit 1; }
          
          echo "3. Force-pushing clean state..."
          git push origin HEAD --force-with-lease
        fi
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubOpen") {
    group = "github"
    description = "Opens the repository in your default browser."
    commandLine("gh", "repo", "view", "--web")
}

tasks.register<Exec>("githubFeature") {
    group = "github"
    description = "Syncs work, then creates a new feature branch and ensures a PR is open."
    dependsOn("githubSync")
    val suffix = if (project.hasProperty("name")) "-${project.property("name")}" else ""
    val script = """
        BRANCH_NAME="feature/${'$'}(date +%Y%m%d-%H%M%S)$suffix"
        git checkout -b "${'$'}BRANCH_NAME"
        git push -u origin "${'$'}BRANCH_NAME"
        gh pr create --fill || echo "PR already exists."
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubMain") {
    group = "github"
    description = "Syncs work and returns to a fresh, updated main branch."
    dependsOn("githubSync")
    commandLine("bash", "-c", "git checkout main && git fetch origin main && git reset --hard origin/main")
}

tasks.register<Exec>("githubPR") {
    group = "github"
    description = "Syncs current work and ensures a PR exists (idempotent)."
    dependsOn("githubSync")
    commandLine("bash", "-c", "gh pr create --fill || echo 'PR already exists or no changes.'")
}

tasks.register<Exec>("githubMerge") {
    group = "github"
    description = "Syncs, updates, and enables auto-merge for the current branch."
    dependsOn("githubPR")
    val script = """
        PR_NUM=$(gh pr view --json number -q .number)
        echo "🚀 Enabling auto-merge for PR #${'$'}PR_NUM..."
        gh pr merge "${'$'}PR_NUM" --auto --squash --delete-branch
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubMergeAll") {
    group = "github"
    description = "Updates and enables auto-merge for all OPEN Pull Requests."
    dependsOn("githubSync")
    val script = """
        echo "🔍 Fetching open pull requests..."
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
              echo "🔄 Syncing branch with main..."
              gh pr update-branch "${'$'}pr" || echo "❌ Update failed."
            fi
            echo "🚀 Enabling auto-merge..."
            gh pr merge "${'$'}pr" --auto --squash --delete-branch || echo "❌ Merge failed."
          done
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubFixAll") {
    group = "github"
    description = "Attempts to fix ALL open PRs by updating branches, resolving 'out-of-date' status, and rerunning failed CI."
    dependsOn("githubSync")
    val script = """
        echo "🔍 Investigating all open Pull Requests..."
        gh pr list --state open --json number,title,mergeable,mergeStateStatus,headRefName --template \
          '{{range .}}{{.number}}{{"\t"}}{{.mergeable}}{{"\t"}}{{.mergeStateStatus}}{{"\t"}}{{.headRefName}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
          while IFS=${'$'}'\t' read -r pr mergeable status branch title; do
            echo "------------------------------------------------------------"
            echo "PR #${'$'}pr: ${'$'}title"
            
            # 1. Resolve 'BEHIND' status automatically
            if [ "${'$'}status" = "BEHIND" ]; then
              echo "🔄 Branch is out-of-date. Syncing with main..."
              gh pr update-branch "${'$'}pr" || echo "❌ Automatic update failed."
            fi

            # 2. Check for CI failures and rerun them
            echo "📉 Checking CI status for branch '${'$'}branch'..."
            RUN_ID=${'$'}(gh run list --branch "${'$'}branch" --status failure --limit 1 --json databaseId -q '.[].databaseId')
            if [ -n "${'$'}RUN_ID" ]; then
              echo "🛠️  Found failed CI run (${'$'}RUN_ID). Triggering rerun..."
              gh run rerun "${'$'}RUN_ID" || echo "❌ Rerun trigger failed."
            else
              echo "✅ No failed CI runs found for this branch."
            fi

            # 3. Enable auto-merge if possible
            if [ "${'$'}mergeable" = "MERGEABLE" ]; then
              echo "🚀 Enabling auto-merge (squash)..."
              gh pr merge "${'$'}pr" --auto --squash --delete-branch || echo "❌ Auto-merge enablement failed."
            fi
          done
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubFixSecurity") {
    group = "github"
    description = "Checks and attempts to resolve all Dependabot, Security, and Scanning alerts."
    dependsOn("githubSync")
    val script = """
        REPO=${'$'}(gh repo view --json nameWithOwner -q .nameWithOwner)
        echo "🛡️  Checking for Dependabot Security Alerts in ${'$'}REPO..."
        gh api "repos/${'$'}REPO/dependabot/alerts" -f state=open --jq '.[] | "🚨 [Dependabot] \(.security_advisory.summary) (\(.dependency.package.name))"' || echo "No Dependabot alerts or error fetching."

        echo "🔍 Checking for Code Scanning Alerts in ${'$'}REPO..."
        gh api "repos/${'$'}REPO/code-scanning/alerts" -f state=open --jq '.[] | "⚠️  [Scanning] \(.rule.description) in \(.most_recent_instance.location.path)"' || echo "No Scanning alerts or error fetching."

        echo "------------------------------------------------------------"
        echo "🚀 Triggering bulk merge for all available security fixes..."
        gh pr list --state open --json number,title --template '{{range .}}{{.number}}{{"\t"}}{{.title}}{{"\n"}}{{end}}' | \
          grep -E "dependabot|security|chore: automated sync" | \
          while IFS=${'$'}'\t' read -r pr title; do
            echo "Processing Security PR #${'$'}pr: ${'$'}title"
            gh pr update-branch "${'$'}pr" 2>/dev/null || true
            gh pr merge "${'$'}pr" --auto --squash --delete-branch || true
          done
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubSetup") {
    group = "github"
    description = "Optimizes GitHub repository settings for this workflow."
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
