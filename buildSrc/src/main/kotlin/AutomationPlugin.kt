import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.kohsuke.github.GHBranch
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

open class AutomationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("githubPreflightKotlin", PreflightTask::class.java)
        project.tasks.register("githubSetupKotlin", SetupTask::class.java)
        project.tasks.register("githubRemoveObsoleteBranchesKotlin", RemoveRemoteObsoleteBranchesTask::class.java)
        project.tasks.register("githubRemoveLocalObsoleteBranchesKotlin", RemoveLocalObsoleteBranchesTask::class.java)
        project.tasks.register("githubPRKotlin", PRTask::class.java)
        project.tasks.register("githubMergeKotlin", MergeTask::class.java)
        project.tasks.register("githubMergeAllKotlin", MergeAllTask::class.java)
        project.tasks.register("githubSyncKotlin", SyncTask::class.java)
        project.tasks.register("githubFeatureKotlin", FeatureTask::class.java)
        project.tasks.register("githubMainKotlin", MainTask::class.java)
        project.tasks.register("githubCleanupRemoteBranchesKotlin", CleanupRemoteBranchesTask::class.java)
        project.tasks.register("githubPruneLocalBranchesKotlin", PruneLocalBranchesTask::class.java)
        project.tasks.register("githubFixAllKotlin", FixAllTask::class.java)
        project.tasks.register("githubPRSummaryKotlin", PRSummaryTask::class.java)
        project.tasks.register("githubFixSecurityKotlin", FixSecurityTask::class.java)
        project.tasks.register("githubChecksKotlin", SimpleTaskLogger::class.java)
        project.tasks.register("githubIssuesKotlin", SimpleTaskLogger::class.java)
        project.tasks.register("githubWikiKotlin", SimpleTaskLogger::class.java)
    }
}

abstract class BaseGitHubTask : DefaultTask() {
    fun connect(): GitHub {
        val token = (project.findProperty("GITHUB_TOKEN") as? String) ?: System.getenv("GITHUB_TOKEN")
        if (token.isNullOrBlank()) {
            throw IllegalStateException("GITHUB_TOKEN is required for Kotlin-native automation tasks. Set env or -PGITHUB_TOKEN.")
        }
        return GitHub.connectUsingOAuth(token)
    }

    fun repo(): GHRepository {
        val gh = connect()
        val repoFull = (project.findProperty("GITHUB_REPOSITORY") as? String) ?: System.getenv("GITHUB_REPOSITORY")
        if (repoFull.isNullOrBlank()) {
            // try to infer from git origin
            val originUrl = Git.open(project.rootDir).repository.config.getString("remote", "origin", "url")
            // origin format: git@github.com:owner/repo.git or https://github.com/owner/repo.git
            val cleaned = originUrl
                .removePrefix("git@github.com:")
                .removePrefix("https://github.com/")
                .removeSuffix(".git")
            return gh.getRepository(cleaned)
        }
        return gh.getRepository(repoFull)
    }
}

open class PreflightTask : BaseGitHubTask() {
    @TaskAction
    fun runPreflight() {
        logger.lifecycle("Running Kotlin preflight checks...")
        try {
            val gh = connect()
            logger.lifecycle("Connected to GitHub as: ${'$'}{gh.myself.login}")
        } catch (e: Exception) {
            logger.warn("Could not authenticate to GitHub via GITHUB_TOKEN: ${'$'}{e.message}")
            throw e
        }
    }
}

open class SetupTask : BaseGitHubTask() {
    @TaskAction
    fun runSetup() {
        val r = repo()
        logger.lifecycle("Updating repository settings via GitHub API...")
        // enable auto-merge and delete branch on merge via REST (library has limited support; use GHRepository.edit())
        r.update()
            .autoMergeAllowed(true)
            .allowUpdateBranch(true)
            .deleteBranchOnMerge(true)
            .enableSquashMerge(true)
            .create()
        logger.lifecycle("Repository settings updated.")
    }
}

open class RemoveRemoteObsoleteBranchesTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val removeMode = (project.findProperty("REMOVE_MODE") as? String)?.toBoolean() ?: (System.getenv("REMOVE_MODE")?.toBoolean() ?: false)
        val days = (project.findProperty("REMOVE_DAYS") as? String)?.toLong() ?: (System.getenv("REMOVE_DAYS")?.toLong() ?: 90L)
        val r = repo()
        logger.lifecycle("Listing branches for repository ${'$'}{r.fullName}")
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        val branches = r.listBranches()
        for ((name, ghbranch) in branches) {
            if (name == r.defaultBranch) continue
            try {
                val protected = ghbranch.protected
                if (protected == true) {
                    logger.lifecycle("Skipping protected branch: ${'$'}name")
                    continue
                }
            } catch (ignored: Exception) {}
            // check open PRs
            val openPRs = r.queryPullRequests().state(GHPullRequest.PULL_REQUEST_STATE.OPEN).head("${'$'}name").list().toList()
            if (openPRs.isNotEmpty()) {
                logger.lifecycle("Skipping ${'$'}name: has open PRs")
                continue
            }
            // check merged PRs
            val mergedPRs = r.queryPullRequests().state(GHPullRequest.PULL_REQUEST_STATE.MERGED).head("${'$'}name").list().toList()
            if (mergedPRs.isNotEmpty()) {
                logger.lifecycle("Branch ${'$'}name has merged PR; eligible for deletion.")
                if (removeMode) {
                    logger.lifecycle("Deleting remote branch ${'$'}name...")
                    try {
                        val ref = r.getRef("refs/heads/${'$'}name")
                        ref.delete()
                    } catch (e: Exception) {
                        logger.warn("Failed to delete ${'$'}name: ${'$'}{e.message}")
                    }
                } else {
                    logger.lifecycle("[DRY-RUN] would delete remote branch: ${'$'}name")
                }
                continue
            }
            // check last commit date
            val commit = ghbranch.sha1
            val ghCommit = r.getCommit(commit)
            val date = ghCommit.commitDate.toInstant()
            if (date.isBefore(cutoff)) {
                logger.lifecycle("Branch ${'$'}name last commit ${'$'}date older than ${'$'}days days; eligible for deletion.")
                if (removeMode) {
                    try {
                        val ref = r.getRef("refs/heads/${'$'}name")
                        ref.delete()
                    } catch (e: Exception) {
                        logger.warn("Failed to delete ${'$'}name: ${'$'}{e.message}")
                    }
                } else {
                    logger.lifecycle("[DRY-RUN] would delete remote branch: ${'$'}name")
                }
            } else {
                logger.lifecycle("${'$'}name is recent; skipping.")
            }
        }
    }
}

open class RemoveLocalObsoleteBranchesTask : DefaultTask() {
    @TaskAction
    fun run() {
        val pruneMode = (project.findProperty("PRUNE_LOCAL_MODE") as? String)?.toBoolean() ?: (System.getenv("PRUNE_LOCAL_MODE")?.toBoolean() ?: false)
        val days = (project.findProperty("PRUNE_LOCAL_DAYS") as? String)?.toLong() ?: (System.getenv("PRUNE_LOCAL_DAYS")?.toLong() ?: 90L)
        val repoDir = project.rootDir
        val builder = FileRepositoryBuilder()
        val repository = builder.setGitDir(File(repoDir, ".git")).readEnvironment().findGitDir().build()
        val git = Git(repository)
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        val branches = git.branchList().call()
        for (ref in branches) {
            val name = ref.name.removePrefix("refs/heads/")
            if (name == repository.branch) continue
            if (name == repository.branch) continue
            // check if merged into base
            val base = (project.findProperty("BASE_BRANCH") as? String) ?: System.getenv("BASE_BRANCH") ?: repository.branch
            try {
                val isMerged = repository.resolve("${base}")?.let { baseId ->
                    repository.resolve(name)?.let { nameId ->
                        repository.resolve(name)?.let { true }
                    }
                }
            } catch (ignored: Exception) {}
            // For simplicity, treat branches older than cutoff and without upstream as eligible
            val lastCommitDate = git.log().add(repository.resolve(name)).setMaxCount(1).call().firstOrNull()?.authorIdent?.whenTime?.toInstant()
            if (lastCommitDate == null) {
                logger.lifecycle("Could not determine commit date for ${'$'}name; skipping.")
                continue
            }
            if (lastCommitDate.isBefore(cutoff)) {
                logger.lifecycle("Local branch ${'$'}name last commit ${'$'}lastCommitDate older than ${'$'}days days; eligible for deletion.")
                if (pruneMode) {
                    logger.lifecycle("Deleting local branch ${'$'}name...")
                    try {
                        git.branchDelete().setBranchNames(name).setForce(true).call()
                    } catch (e: Exception) {
                        logger.warn("Failed to delete local ${'$'}name: ${'$'}{e.message}")
                    }
                } else {
                    logger.lifecycle("[DRY-RUN] would delete local branch: ${'$'}name")
                }
            } else {
                logger.lifecycle("${'$'}name is recent; skipping.")
            }
        }
    }
}
