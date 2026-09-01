import java.util.Properties

buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.84") {
                because("CVE-2024-34447")
            }
        }
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.shadow) apply false
}

// Configuration cache compatible versioning helper
fun incrementVersion(version: String, isTest: Boolean): String {
    val isCurrentlyTest = version.contains("-test.")
    val base = version.split("-")[0]
    val parts = base.split(".").map { it.toInt() }.toMutableList()

    return if (isTest) {
        if (isCurrentlyTest) {
            val n = version.split("-test.")[1].toInt()
            "$base-test.${n + 1}"
        } else {
            parts[2] = parts[2] + 1
            "${parts.joinToString(".")}-test.1"
        }
    } else {
        if (isCurrentlyTest) {
            base
        } else {
            parts[2] = parts[2] + 1
            parts.joinToString(".")
        }
    }
}

tasks.register("releaseTest") {
    group = "publishing"
    description = "Increments version for test and pushes tag to GitHub."
    val propsFile = layout.projectDirectory.file("gradle.properties").asFile
    doLast {
        val props = Properties()
        props.load(propsFile.inputStream())
        val current = props["appVersion"] as String
        val next = incrementVersion(current, true)
        props["appVersion"] = next
        props.store(propsFile.outputStream(), "Updated by release task")

        // Manual ProcessBuilder to avoid configuration cache issues with project.exec
        fun git(vararg args: String) {
            val pb = ProcessBuilder("git", *args).inheritIO().start()
            if (pb.waitFor() != 0) throw GradleException("Git command failed: ${args.joinToString(" ")}")
        }

        git("add", "gradle.properties")
        git("commit", "-m", "chore: bump version to $next [test]")
        git("tag", "-a", "v$next", "-m", "Test Release $next")
        git("push", "origin", "HEAD", "--tags")

        println("Successfully triggered test release: v$next")
    }
}

tasks.register("releaseProduction") {
    group = "publishing"
    description = "Increments version for production and pushes tag to GitHub."
    val propsFile = layout.projectDirectory.file("gradle.properties").asFile
    doLast {
        val props = Properties()
        props.load(propsFile.inputStream())
        val current = props["appVersion"] as String
        val next = incrementVersion(current, false)
        props["appVersion"] = next
        props.store(propsFile.outputStream(), "Updated by release task")

        // Manual ProcessBuilder to avoid configuration cache issues with project.exec
        fun git(vararg args: String) {
            val pb = ProcessBuilder("git", *args).inheritIO().start()
            if (pb.waitFor() != 0) throw GradleException("Git command failed: ${args.joinToString(" ")}")
        }

        git("add", "gradle.properties")
        git("commit", "-m", "chore: bump version to $next [production]")
        git("tag", "-a", "v$next", "-m", "Production Release $next")
        git("push", "origin", "HEAD", "--tags")

        println("Successfully triggered production release: v$next")
    }
}

tasks.register<Exec>("pushToGitHub") {
    description = "Pushes the current branch to GitHub (origin)."
    group = "publishing"
    commandLine("git", "push", "origin", "HEAD")
}

tasks.register<Exec>("githubSync") {
    group = "publishing"
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

// --- GitHub Creator Tasks ---

tasks.register<Exec>("githubOpen") {
    group = "github"
    description = "Opens the ACC repository in your default browser."
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
    description = "Attempts to fix failed PRs by updating their branches and rerunning CI."
    dependsOn("githubSync")
    val script = """
        gh pr list --json number -q '.[].number' | while read -r pr; do
          echo "Processing PR #${'$'}pr..."
          gh pr update-branch "${'$'}pr" || true
          BRANCH=${'$'}(gh pr view "${'$'}pr" --json headRefName -q .headRefName)
          RUN_ID=${'$'}(gh run list --branch "${'$'}BRANCH" --status failure --limit 1 --json databaseId -q '.[].databaseId')
          if [ -n "${'$'}RUN_ID" ]; then
            echo "Rerunning failed CI run (${'$'}RUN_ID) for PR #${'$'}pr..."
            gh run rerun "${'$'}RUN_ID" || true
          fi
        done
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubSetup") {
    group = "github"
    description = "Optimizes GitHub repository settings for the ACC workflow."
    commandLine("gh", "repo", "edit", "--enable-auto-merge", "--delete-branch-on-merge", "--allow-update-branch", "--enable-squash-merge")
}

tasks.register<Exec>("githubChecks") {
    group = "github"
    description = "Displays the status of GitHub Action checks for the current branch."
    commandLine("gh", "pr", "checks", "--watch")
}

tasks.register<Exec>("githubIssues") {
    group = "github"
    description = "Lists all open issues for the ACC project."
    commandLine("gh", "issue", "list")
}

tasks.register<Exec>("githubWiki") {
    group = "github"
    description = "Opens the ACC Wiki in your default browser."
    commandLine("gh", "repo", "view", "--web", "--path", "wiki")
}

tasks.register<Exec>("githubActions") {
    group = "github"
    description = "Opens the GitHub Actions tab in your default browser."
    commandLine("gh", "run", "list", "--web")
}
