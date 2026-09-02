import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.kohsuke.github.GHPullRequest
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

// PR, Merge, MergeAll, Sync, CleanupRemoteBranches, PruneLocalBranches, FixAll
open class PRTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val r = repo()
        val branch = (project.findProperty("CURRENT_BRANCH") as? String) ?: System.getenv("CURRENT_BRANCH") ?: ""
        val base = (project.findProperty("BASE_BRANCH") as? String) ?: System.getenv("BASE_BRANCH") ?: r.defaultBranch
        if (branch.isBlank()) {
            logger.lifecycle("No current branch provided; skipping PR creation.")
            return
        }
        val existing = r.queryPullRequests().head(branch).base(base).state(GHPullRequest.PULL_REQUEST_STATE.OPEN).list().toList()
        if (existing.isNotEmpty()) {
            logger.lifecycle("PR already exists for ${branch}")
            return
        }
        val title = "Automated PR: ${branch}"
        val body = "Auto-created PR by automation"
        r.createPullRequest(title, branch, base, body)
        logger.lifecycle("Created PR for ${branch} -> ${base}")
    }
}

open class MergeTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val r = repo()
        val branch = (project.findProperty("CURRENT_BRANCH") as? String) ?: System.getenv("CURRENT_BRANCH") ?: ""
        if (branch.isBlank()) {
            logger.lifecycle("No current branch provided; skipping merge.")
            return
        }
        val prs = r.queryPullRequests().head(branch).state(GHPullRequest.PULL_REQUEST_STATE.OPEN).list().toList()
        if (prs.isEmpty()) {
            logger.lifecycle("No open PR found for ${branch}")
            return
        }
        val pr = prs.first()
        try {
            if (pr.mergeableState == "MERGEABLE") {
                pr.merge("Automated merge by Kotlin tasks")
                logger.lifecycle("Merged PR #${pr.number} for ${branch}")
            } else {
                logger.lifecycle("PR #${pr.number} not mergeable: ${pr.mergeableState}")
            }
        } catch (e: Exception) {
            logger.warn("Merge failed for PR #${pr.number}: ${e.message}")
        }
    }
}

open class MergeAllTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val r = repo()
        val prs = r.listPullRequests(GHPullRequest.PULL_REQUEST_STATE.OPEN)
        for (pr in prs) {
            try {
                if (pr.mergeableState == "MERGEABLE") {
                    logger.lifecycle("Merging PR #${pr.number}: ${pr.title}")
                    pr.merge("Automated mergeAll by Kotlin tasks")
                } else {
                    logger.lifecycle("Skipping PR #${pr.number}: mergeableState=${pr.mergeableState}")
                }
            } catch (e: Exception) {
                logger.warn("Failed to merge PR #${pr.number}: ${e.message}")
            }
        }
    }
}

open class SyncTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        // Minimal safe sync: fetch remote and update local base branch
        val r = repo()
        val git = Git.open(project.rootDir)
        git.fetch().setRemote("origin").call()
        val base = (project.findProperty("BASE_BRANCH") as? String) ?: System.getenv("BASE_BRANCH") ?: r.defaultBranch
        try {
            git.checkout().setName(base).call()
            git.pull().call()
            logger.lifecycle("Updated local ${base} from origin/${base}")
        } catch (e: Exception) {
            logger.warn("Sync failed: ${e.message}")
        }
    }
}

open class CleanupRemoteBranchesTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val t = RemoveRemoteObsoleteBranchesTask()
        t.project = project
        t.run()
    }
}

open class PruneLocalBranchesTask : DefaultTask() {
    @TaskAction
    fun run() {
        val t = RemoveLocalObsoleteBranchesTask()
        t.project = project
        t.run()
    }
}

open class FixAllTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val r = repo()
        val prs = r.listPullRequests(GHPullRequest.PULL_REQUEST_STATE.OPEN)
        for (pr in prs) {
            try {
                logger.lifecycle("Ensuring PR #${pr.number} is up-to-date: ${pr.title}")
                if (pr.mergeableState == "MERGEABLE") {
                    pr.merge("Automated fixAll merge")
                }
            } catch (e: Exception) {
                logger.warn("fixAll: failed for PR #${pr.number}: ${e.message}")
            }
        }
    }
}

open class MainTask : DefaultTask() {
    @TaskAction
    fun run() {
        val git = Git.open(project.rootDir)
        val repo = git.repository
        val base = (project.findProperty("BASE_BRANCH") as? String) ?: System.getenv("BASE_BRANCH") ?: repo.branch
        try {
            git.checkout().setName(base).call()
            git.fetch().setRemote("origin").call()
            val remoteRef = repo.resolve("refs/remotes/origin/${base}")
            if (remoteRef != null) {
                git.reset().setRef(remoteRef.name).setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call()
                logger.lifecycle("Reset local ${base} to origin/${base}")
            } else {
                logger.lifecycle("Remote ref origin/${base} not found; skipping reset")
            }
        } catch (e: Exception) {
            logger.warn("Failed to reset main: ${e.message}")
        }
    }
}

open class FeatureTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val git = Git.open(project.rootDir)
        val featureName = (project.findProperty("featureName") as? String) ?: (project.findProperty("name") as? String) ?: "feature-${System.currentTimeMillis()}"
        val safe = featureName.replace(Regex("[^A-Za-z0-9._/-]"), "-").trim('-')
        val branch = "feature/${safe}"
        try {
            val head = git.repository.findRef("refs/heads/${git.repository.branch}")
            git.branchCreate().setName(branch).setStartPoint(head.name).call()
            git.push().setRemote("origin").add(branch).call()
            logger.lifecycle("Created and pushed branch ${branch}")
        } catch (e: Exception) {
            logger.warn("Failed to create feature branch: ${e.message}")
        }
    }
}

// MainTask is implemented below
open class MainTask : DefaultTask() {
    @TaskAction
    fun run() {
        val git = Git.open(project.rootDir)
        val repo = git.repository
        val base = (project.findProperty("BASE_BRANCH") as? String) ?: System.getenv("BASE_BRANCH") ?: repo.branch
        try {
            git.checkout().setName(base).call()
            git.fetch().setRemote("origin").call()
            val remoteRef = repo.resolve("refs/remotes/origin/${base}")
            if (remoteRef != null) {
                git.reset().setRef(remoteRef.name).setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call()
                logger.lifecycle("Reset local ${base} to origin/${base}")
            } else {
                logger.lifecycle("Remote ref origin/${base} not found; skipping reset")
            }
        } catch (e: Exception) {
            logger.warn("Failed to reset main: ${e.message}")
        }
    }
}
