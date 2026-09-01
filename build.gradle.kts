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
    description = "Automatically adds, commits, and pushes all changes to GitHub (handles protected main branch)."
    val script = """
        CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
        git add .
        if ! git diff --cached --quiet; then
          git commit -m 'chore: automated sync to GitHub' || true
        fi

        if [ "${'$'}CURRENT_BRANCH" = "main" ]; then
          SYNC_BRANCH="sync/${'$'}(date +%Y%m%d-%H%M%S)"
          echo "On main branch. Creating sync branch: ${'$'}SYNC_BRANCH"
          git checkout -b "${'$'}SYNC_BRANCH"
          git push -u origin "${'$'}SYNC_BRANCH"
          if command -v gh >/dev/null 2>&1; then
            gh pr create --fill || echo "PR creation failed (maybe it already exists or no changes)"
          else
            echo "gh CLI not found, skipping PR creation."
          fi
          git checkout main
          git fetch origin main
          git reset --hard origin/main
        else
          git push origin HEAD
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
    description = "Syncs current work, then creates a new timestamped feature branch and pushes it to origin."
    dependsOn("githubSync")
    val suffix = if (project.hasProperty("name")) "-${project.property("name")}" else ""
    val script = """
        BRANCH_NAME="feature/${'$'}(date +%Y%m%d-%H%M%S)$suffix"
        git checkout -b "${'$'}BRANCH_NAME"
        git push -u origin "${'$'}BRANCH_NAME"
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubMain") {
    group = "github"
    description = "Syncs current work, then switches back to the 'main' branch and pulls latest from origin."
    dependsOn("githubSync")
    commandLine("bash", "-c", "git checkout main && git fetch origin main && git reset --hard origin/main")
}

tasks.register<Exec>("githubPR") {
    group = "github"
    description = "Syncs current work, then creates a Pull Request for the current branch to 'main' (idempotent)."
    dependsOn("githubSync")
    commandLine("bash", "-c", "gh pr create --fill || echo 'PR already exists or no changes to push.'")
}

tasks.register<Exec>("githubMerge") {
    group = "github"
    description = "Updates the PR branch from main, then merges it automatically (requires 'Allow auto-merge' in repo settings)."
    dependsOn("githubPR")
    val script = """
        PR_NUM=$(gh pr view --json number -q .number)
        echo "Updating and merging PR #${'$'}PR_NUM..."
        gh pr update-branch "${'$'}PR_NUM" || echo "Note: Branch update skipped or failed."
        gh pr merge "${'$'}PR_NUM" --auto --squash --delete-branch
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubMergeAll") {
    group = "github"
    description = "Attempts to update and merge ALL open Pull Requests automatically."
    val script = """
        gh pr list --json number -q '.[].number' | while read -r pr; do
          echo "Processing PR #${'$'}pr..."
          # 1. Update branch from main (resolves out-of-date/stale failures)
          gh pr update-branch "${'$'}pr" || echo "Note: Branch update skipped or failed for #${'$'}pr"
          
          # 2. Enable auto-merge
          gh pr merge "${'$'}pr" --auto --squash --delete-branch
        done
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubFixAll") {
    group = "github"
    description = "Attempts to fix failed PRs by updating their branches from 'main' and rerunning failed CI checks."
    val script = """
        gh pr list --json number -q '.[].number' | while read -r pr; do
          echo "Processing PR #${'$'}pr..."
          # 1. Update branch from main (resolves out-of-date/stale failures)
          gh pr update-branch "${'$'}pr" || echo "Note: Branch update skipped or failed for #${'$'}pr"
          
          # 2. Find and rerun the most recent failed CI run for this PR branch
          BRANCH=${'$'}(gh pr view "${'$'}pr" --json headRefName -q .headRefName)
          RUN_ID=${'$'}(gh run list --branch "${'$'}BRANCH" --status failure --limit 1 --json databaseId -q '.[].databaseId')
          if [ -n "${'$'}RUN_ID" ]; then
            echo "Rerunning failed CI run (${'$'}RUN_ID) for PR #${'$'}pr..."
            gh run rerun "${'$'}RUN_ID" || echo "Rerun failed for run ID ${'$'}RUN_ID"
          fi
        done
    """.trimIndent()
    commandLine("bash", "-c", script)
}

tasks.register<Exec>("githubSetup") {
    group = "github"
    description = "Configures the GitHub repository settings for the optimal ACC workflow (Auto-merge, branch deletion, etc)."
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
