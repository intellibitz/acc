plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "cc.thevar.acc"
version = "1.0.0"

application {
    mainClass.set("cc.thevar.acc.ApplicationKt")
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
    implementation(libs.logback)
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
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.mockk)
}
