plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "cc.thevar.acc"
version = "1.0.0"

application {
    mainClass.set("cc.thevar.acc.ApplicationKt")
}

dependencies {
    api(project(":common"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
