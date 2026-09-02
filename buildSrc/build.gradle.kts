plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202305101054-r")
    implementation("org.kohsuke:github-api:1.332")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}
