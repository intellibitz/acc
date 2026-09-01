import java.util.Properties

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

fun getAppVersion(): String = project.property("appVersion") as String

fun updateAppVersion(newVersion: String) {
    val propsFile = file("gradle.properties")
    val props = Properties()
    props.load(propsFile.inputStream())
    props["appVersion"] = newVersion
    props.store(propsFile.outputStream(), "Updated by release task")
}

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

fun git(vararg args: String) {
    val process = ProcessBuilder("git", *args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("Git command failed with exit code $exitCode: $output")
    }
}

tasks.register("releaseTest") {
    group = "publishing"
    description = "Increments version for test and pushes tag to GitHub."
    doLast {
        val current = getAppVersion()
        val next = incrementVersion(current, true)
        updateAppVersion(next)

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
    doLast {
        val current = getAppVersion()
        val next = incrementVersion(current, false)
        updateAppVersion(next)

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

tasks.register("syncToGitHub") {
    group = "publishing"
    description = "Automatically adds, commits (generic message), and pushes all changes to GitHub."
    doLast {
        // 1. Add everything
        git("add", ".")

        // 2. Try to commit (ignore error if nothing to commit)
        try {
            git("commit", "-m", "chore: automated sync to GitHub")
        } catch (e: Exception) {
            if (e.message?.contains("nothing to commit") == true) {
                println("Nothing to commit, proceeding to push.")
            } else {
                throw e
            }
        }

        // 3. Push
        git("push", "origin", "HEAD")
        println("Successfully synced changes to GitHub.")
    }
}
