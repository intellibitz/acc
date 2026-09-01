import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.shadow)
    // Removed application plugin to avoid broken shadow distribution tasks in Gradle 8+
}

group = "cc.thevar.acc"
version = project.property("appVersion") as String

val mainClassName = "cc.thevar.acc.ApplicationKt"

tasks.register<JavaExec>("run") {
    group = "application"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = mainClassName
    }
}

tasks.register<Copy>("copyWasmFrontend") {
    dependsOn(":frontend:web:wasmJsBrowserDistribution")
    from(project(":frontend:web").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(project.layout.buildDirectory.dir("resources/main/static"))
}

tasks.processResources {
    dependsOn("copyWasmFrontend")
}

dependencies {
    api(project(":common"))
    implementation(libs.oshi.core)
    implementation(libs.kotlinx.io)
    implementation(libs.logback)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.mockk)
}
