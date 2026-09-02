import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.DefaultTask

// Implement tasks using 'git' and 'gh' CLIs for portability and to avoid external Java deps.

open class PRTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val branch = currentBranchInput
        val base = if (baseBranchInput.isNotBlank()) baseBranchInput else "main"
        val repo = repoFull()
        if (branch.isBlank()) {
            logger.lifecycle("No current branch provided; skipping PR creation.")
            return
        }
        // Check existing PR for branch
        val (rc, out) = runCmd("gh", "pr", "list", "--head", branch, "--repo", repo, "--json", "number", "-q", ".[0].number")
        if (rc == 0 && out.isNotBlank()) {
            logger.lifecycle("PR already exists for ${branch}")
            return
        }
        val title = "Automated PR: ${branch}"
        val body = "Auto-created PR by automation"
        val (_, createOut) = runCmd("gh", "pr", "create", "--title", title, "--body", body, "--head", branch, "--base", base, "--repo", repo, "--assume-yes")
        logger.lifecycle("Created PR for ${branch} -> ${base}: ${createOut}")
    }
}

open class MergeTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val branch = currentBranchInput
        val repo = repoFull()
        if (branch.isBlank()) {
            logger.lifecycle("No current branch provided; skipping merge.")
            return
        }
        val (rc, prNum) = runCmd("gh", "pr", "list", "--head", branch, "--repo", repo, "--json", "number", "-q", ".[0].number")
        if (rc != 0 || prNum.isBlank()) {
            logger.lifecycle("No open PR found for ${branch}")
            return
        }
        val (mrc, mout) = runCmd("gh", "pr", "merge", prNum, "--squash", "--delete-branch", "--repo", repo, "--confirm")
        if (mrc == 0) logger.lifecycle("Merged PR #${prNum} for ${branch}") else logger.warn("Merge failed: ${mout}")
    }
}

open class MergeAllTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val repo = repoFull()
        val (rc, out) = runCmd("gh", "pr", "list", "--state", "open", "--repo", repo, "--json", "number,mergeable,mergeStateStatus", "-q", ".[] | [.number, .mergeable, .mergeStateStatus] | @tsv")
        if (rc != 0) { logger.warn("Failed to list PRs: ${out}"); return }
        out.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\t')
            val pr = parts.getOrNull(0) ?: return@forEach
            val mergeable = parts.getOrNull(1) ?: ""
            val status = parts.getOrNull(2) ?: ""
            if (mergeable == "CONFLICTING" || mergeable == "DRAFT") {
                logger.lifecycle("Skipping PR #${pr}: conflicted or draft")
                return@forEach
            }
            logger.lifecycle("Attempting to merge PR #${pr} (status=${status})")
            val (mrc, mout) = runCmd("gh", "pr", "merge", pr, "--auto", "--squash", "--delete-branch", "--repo", repo)
            if (mrc != 0) logger.warn("Merge/enable auto-merge failed for #${pr}: ${mout}") else logger.lifecycle("Enabled merge for #${pr}")
        }
    }
}

open class SyncTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val repoDir = rootDir
        val base = if (baseBranchInput.isNotBlank()) baseBranchInput else "main"
        runCmd("git", "fetch", "origin", workingDir = repoDir)
        val (rc, out) = runCmd("git", "checkout", base, workingDir = repoDir)
        if (rc != 0) { logger.warn("Checkout failed: ${out}"); return }
        val (rc2, out2) = runCmd("git", "pull", "origin", base, workingDir = repoDir)
        if (rc2 == 0) logger.lifecycle("Updated local ${base} from origin/${base}") else logger.warn("Pull failed: ${out2}")
    }
}

open class CleanupRemoteBranchesTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val repo = repoFull()
        logger.lifecycle("Invoking remote branch cleanup for ${repo} (dry-run unless REMOVE_MODE=true)")
        val (rc, out) = runCmd("gh", "api", "repos/:owner/:repo/branches", "-H", "Accept: application/vnd.github+json", "-F", "repo=${repo}")
        if (rc != 0) logger.warn("Failed to list branches: ${out}") else logger.lifecycle("Retrieved branches metadata (see logs for details).")
    }
}

open class PruneLocalBranchesTask : DefaultTask() {
    @get:Internal
    val rootDir: File = project.rootDir

    @get:Input
    val pruneModeInput: Boolean = (project.findProperty("PRUNE_LOCAL_MODE") as? String)?.toBoolean() ?: (System.getenv("PRUNE_LOCAL_MODE")?.toBoolean() ?: false)

    @get:Input
    val pruneDaysInput: Long = (project.findProperty("PRUNE_LOCAL_DAYS") as? String)?.toLong() ?: (System.getenv("PRUNE_LOCAL_DAYS")?.toLong() ?: 90L)

    @TaskAction
    fun run() {
        val repoDir = rootDir
        val cutoff = Instant.now().minus(pruneDaysInput, ChronoUnit.DAYS)
        logger.lifecycle("Pruning local branches older than ${pruneDaysInput} days. Dry-run=${!pruneModeInput}")
        val (rc, out) = runCmd("git", "for-each-ref", "--format=%(refname:short) %(committerdate:iso8601)", "refs/heads/", workingDir = repoDir)
        if (rc != 0) { logger.warn("Failed to enumerate local branches: ${out}"); return }
        out.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split(" ", limit = 2)
            if (parts.size < 2) return@forEach
            val name = parts[0]
            val dateStr = parts[1]
            try {
                val date = Instant.parse(dateStr)
                if (date.isBefore(cutoff)) {
                    logger.lifecycle("Local branch ${name} last commit ${date} older than ${pruneDaysInput} days; eligible for deletion.")
                    if (pruneModeInput) {
                        val (rcd, od) = runCmd("git", "branch", "-D", name, workingDir = repoDir)
                        if (rcd == 0) logger.lifecycle("Deleted local branch ${name}") else logger.warn("Failed delete ${name}: ${od}")
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

open class FixAllTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val repo = repoFull()
        val (rc, out) = runCmd("gh", "pr", "list", "--state", "open", "--repo", repo, "--json", "number,mergeable,mergeStateStatus", "-q", ".[] | [.number, .mergeable, .mergeStateStatus] | @tsv")
        if (rc != 0) { logger.warn("Failed to list PRs: ${out}"); return }
        out.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\t')
            val pr = parts.getOrNull(0) ?: return@forEach
            val mergeable = parts.getOrNull(1) ?: ""
            if (mergeable == "MERGEABLE") {
                val (mrc, mout) = runCmd("gh", "pr", "merge", pr, "--squash", "--delete-branch", "--repo", repo)
                if (mrc != 0) logger.warn("Failed to merge #${pr}: ${mout}") else logger.lifecycle("Merged #${pr}")
            }
        }
    }
}

open class PRSummaryTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val repo = repoFull()
        logger.lifecycle("Open PRs:")
        val (rc, open) = runCmd("gh", "pr", "list", "--state", "open", "--repo", repo, "--json", "number,title,headRefName,mergeable,mergeStateStatus", "-q", ".[] | [.number, .title, .headRefName, .mergeable, .mergeStateStatus] | @tsv")
        if (rc == 0) {
            open.lines().forEach { logger.lifecycle(it) }
        } else logger.warn("Failed to list open PRs: ${open}")
        logger.lifecycle("\nMerged PRs (recent):")
        val (rc2, merged) = runCmd("gh", "pr", "list", "--state", "merged", "--repo", repo, "--json", "number,title,headRefName", "-q", ".[] | [.number, .title, .headRefName] | @tsv")
        if (rc2 == 0) merged.lines().forEach { logger.lifecycle(it) } else logger.warn("Failed to list merged PRs: ${merged}")
    }
}

open class FixSecurityTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val repo = repoFull()
        logger.lifecycle("Looking for Dependabot/security PRs to merge...")
        val (rc, out) = runCmd("gh", "pr", "list", "--state", "open", "--repo", repo, "--json", "number,title", "-q", ".[] | [.number, .title] | @tsv")
        if (rc != 0) { logger.warn("Failed to list PRs: ${out}"); return }
        out.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\t')
            val pr = parts.getOrNull(0) ?: return@forEach
            val title = parts.getOrNull(1) ?: ""
            val t = title.lowercase()
            if (t.contains("dependabot") || t.contains("security")) {
                val (mrc, mout) = runCmd("gh", "pr", "merge", pr, "--auto", "--squash", "--delete-branch", "--repo", repo)
                if (mrc != 0) logger.warn("Failed to merge security PR #${pr}: ${mout}") else logger.lifecycle("Merged security PR #${pr}: ${title}")
            }
        }
    }
}

open class SimpleTaskLogger : DefaultTask() {
    @TaskAction
    fun run() {
        logger.lifecycle("This is a placeholder task for simple actions (issues/wiki/checks).")
    }
}


open class FeatureTask : BaseGitHubTask() {
    @get:Input
    val featureNameInput: String = (project.findProperty("featureName") as? String) ?: (project.findProperty("name") as? String) ?: ""

    @TaskAction
    fun run() {
        val repoDir = rootDir
        val nameToUse = if (featureNameInput.isNotBlank()) featureNameInput else "feature-${System.currentTimeMillis()}"
        val safe = nameToUse.replace(Regex("[^A-Za-z0-9._/-]"), "-").trim('-')
        val branch = "feature/${safe}"
        val (rc, out) = runCmd("git", "checkout", "-b", branch, workingDir = repoDir)
        if (rc != 0) { logger.warn("Failed to create branch: ${out}"); return }
        val (pRc, pOut) = runCmd("git", "push", "-u", "origin", branch, workingDir = repoDir)
        if (pRc == 0) logger.lifecycle("Created and pushed branch ${branch}") else logger.warn("Failed to push branch: ${pOut}")
    }
}

// MainTask is implemented below
open class MainTask : DefaultTask() {
    @get:Internal
    val rootDir: File = project.rootDir

    @get:Input
    val baseBranchInput: String = (project.findProperty("BASE_BRANCH") as? String) ?: System.getenv("BASE_BRANCH") ?: "main"

    @TaskAction
    fun run() {
        val repoDir = rootDir
        val base = if (baseBranchInput.isNotBlank()) baseBranchInput else "main"
        runCmd("git", "checkout", base, workingDir = repoDir)
        runCmd("git", "fetch", "origin", workingDir = repoDir)
        val (rc2, out2) = runCmd("git", "reset", "--hard", "origin/${base}", workingDir = repoDir)
        if (rc2 == 0) logger.lifecycle("Reset local ${base} to origin/${base}") else logger.warn("Failed to reset main: ${out2}")
    }
}
