import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

// Lightweight helpers using 'git' and 'gh' CLIs where available.
fun runCmd(vararg cmd: String, workingDir: File? = null, env: Map<String, String> = emptyMap()): Pair<Int, String> {
    val pb = ProcessBuilder(*cmd)
    if (workingDir != null) pb.directory(workingDir)
    val e = pb.environment()
    e.putAll(env)
    pb.redirectErrorStream(true)
    val proc = pb.start()
    val out = proc.inputStream.bufferedReader().readText()
    val rc = proc.waitFor()
    return rc to out.trim()
}

fun inferRepoFromGit(root: File): String? {
    val (rc, out) = runCmd("git", "remote", "get-url", "origin", workingDir = root)
    if (rc != 0 || out.isBlank()) return null
    var cleaned = out
        .removePrefix("git@github.com:")
        .removePrefix("https://github.com/")
        .removeSuffix(".git")
    cleaned = cleaned.trim()
    return cleaned
}

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
    fun token(): String {
        val propKeys = listOf("GITHUB_TOKEN", "githubToken", "github.token", "GH_TOKEN", "ghToken")
        for (k in propKeys) {
            val v = project.findProperty(k) as? String
            if (!v.isNullOrBlank()) return v.trim()
        }
        val env = System.getenv()
        val envTok = env["GITHUB_TOKEN"] ?: env["GH_TOKEN"] ?: env["github_token"]
        if (!envTok.isNullOrBlank()) return envTok.trim()
        val home = System.getProperty("user.home") ?: ""
        try {
            val f = java.io.File(home, ".gradle/gradle.properties")
            if (f.exists()) {
                val props = java.util.Properties()
                f.inputStream().use { props.load(it) }
                for (k in propKeys) {
                    val v = props.getProperty(k)
                    if (!v.isNullOrBlank()) return v.trim()
                }
            }
        } catch (_: Exception) {}
        throw IllegalStateException("GITHUB_TOKEN is required for Kotlin-native automation tasks. Provide via gradle properties or env.")
    }

    fun repoFull(): String {
        val repoFull = (project.findProperty("GITHUB_REPOSITORY") as? String) ?: System.getenv("GITHUB_REPOSITORY")
        if (!repoFull.isNullOrBlank()) return repoFull.trim()
        val inferred = inferRepoFromGit(project.rootDir)
        if (!inferred.isNullOrBlank()) return inferred
        throw IllegalStateException("Could not determine repository owner/name. Set -PGITHUB_REPOSITORY or ensure 'git' remote origin is configured.")
    }
}

open class PreflightTask : BaseGitHubTask() {
    @TaskAction
    fun runPreflight() {
        logger.lifecycle("Running Kotlin preflight checks...")
        try {
            val t = token()
            logger.lifecycle("Token available (masked). Will validate gh auth if available.")
            val (rc, out) = runCmd("gh", "auth", "status")
            if (rc == 0) {
                logger.lifecycle("gh CLI authenticated: ${'$'}out")
            } else {
                logger.lifecycle("gh CLI not authenticated or not present: ${'$'}out")
            }
        } catch (e: Exception) {
            logger.warn("Preflight failed: ${'$'}{e.message}")
            throw e
        }
    }
}

open class SetupTask : BaseGitHubTask() {
    @TaskAction
    fun runSetup() {
        val repo = repoFull()
        logger.lifecycle("Updating repository settings via gh...")
        val (rc, out) = runCmd("gh", "repo", "edit", repo, "--enable-auto-merge", "--delete-branch-on-merge", "--allow-update-branch", "--enable-squash-merge")
        if (rc != 0) logger.warn("gh repo edit failed: ${'$'}out") else logger.lifecycle("Repository settings updated.")
    }
}

open class RemoveRemoteObsoleteBranchesTask : BaseGitHubTask() {
    @TaskAction
    fun run() {
        val removeMode = (project.findProperty("REMOVE_MODE") as? String)?.toBoolean() ?: (System.getenv("REMOVE_MODE")?.toBoolean() ?: false)
        val days = (project.findProperty("REMOVE_DAYS") as? String)?.toLong() ?: (System.getenv("REMOVE_DAYS")?.toLong() ?: 90L)
        val repo = repoFull()
        logger.lifecycle("Listing branches for repository ${'$'}repo")
        // Use gh to list branches in simple form and process locally
        val (rc, out) = runCmd("gh", "api", "repos/:owner/:repo/branches", "-H", "Accept: application/vnd.github+json", "-F", "repo=${repo}")
        if (rc != 0) { logger.warn("Failed to list branches: ${'$'}out"); return }
        logger.lifecycle("Branch listing retrieved. Use removeMode=${'$'}removeMode and cutoff=${'$'}days days")
        logger.lifecycle("Note: detailed branch pruning requires API parsing; run githubRemoveObsoleteBranchesKotlin for safer operation.")
    }
}

open class RemoveLocalObsoleteBranchesTask : DefaultTask() {
    @TaskAction
    fun run() {
        val pruneMode = (project.findProperty("PRUNE_LOCAL_MODE") as? String)?.toBoolean() ?: (System.getenv("PRUNE_LOCAL_MODE")?.toBoolean() ?: false)
        val days = (project.findProperty("PRUNE_LOCAL_DAYS") as? String)?.toLong() ?: (System.getenv("PRUNE_LOCAL_DAYS")?.toLong() ?: 90L)
        val repoDir = project.rootDir
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        logger.lifecycle("Pruning local branches older than ${'$'}days days. Dry-run=${'$'}!pruneMode")
        val (rc, out) = runCmd("git", "for-each-ref", "--format=%(refname:short) %(committerdate:iso8601)", "refs/heads/", workingDir = repoDir)
        if (rc != 0) { logger.warn("Failed to enumerate local branches: ${'$'}out"); return }
        out.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split(" ", limit = 2)
            if (parts.size < 2) return@forEach
            val name = parts[0]
            val dateStr = parts[1]
            try {
                val date = Instant.parse(dateStr)
                if (date.isBefore(cutoff)) {
                    logger.lifecycle("Local branch ${'$'}name last commit ${'$'}date older than ${'$'}days days; eligible for deletion.")
                    if (pruneMode) {
                        val (rcd, od) = runCmd("git", "branch", "-D", name, workingDir = repoDir)
                        if (rcd == 0) logger.lifecycle("Deleted local branch ${'$'}name") else logger.warn("Failed delete ${'$'}name: ${'$'}od")
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
