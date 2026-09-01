package cc.thevar.acc

import cc.thevar.acc.di.gatewayModule
import cc.thevar.acc.routing.configureRouting
import cc.thevar.acc.service.MonitoringService
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyStore
import kotlin.time.Duration.Companion.seconds

private fun findProjectRoot(): File {
    val envRoot = System.getenv("ACC_ROOT")?.let { File(it) }
    if (envRoot != null && envRoot.exists()) return envRoot

    val userDir = System.getProperty("user.dir")?.let { File(it) } ?: File(".")
    var current: File? = userDir.absoluteFile

    while (current != null) {
        if (File(current, "acc").exists() && File(current, "settings.gradle.kts").exists()) return current
        if (File(current, "acc").exists() && File(current, "config").exists() && File(current, "data").exists()) return current
        current = current.parentFile
    }
    return userDir.absoluteFile
}

val projectRoot = findProjectRoot()
private val logger = LoggerFactory.getLogger("cc.thevar.acc.Main")

fun main() {
    logger.info("Starting from: {}", System.getProperty("user.dir"))
    logger.info("Detected Project Root: {}", projectRoot.absolutePath)

    val keyStoreFile = File(projectRoot, "config/keystore.p12")
    val httpPort = 8333
    val httpsPort = 8334

    embeddedServer(Netty, configure = {
        connector {
            port = httpPort
            host = "0.0.0.0"
        }

        if (keyStoreFile.exists()) {
            val keyStorePassword = System.getenv("ACC_KEYSTORE_PASSWORD")?.toCharArray() ?: "password".toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStoreFile.inputStream().use { keyStore.load(it, keyStorePassword) }

            sslConnector(
                keyStore = keyStore,
                keyAlias = "acc",
                keyStorePassword = { keyStorePassword },
                privateKeyPassword = { keyStorePassword }
            ) {
                port = httpsPort
                keyStorePath = keyStoreFile
                host = "0.0.0.0"
            }
        }
    }) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(gatewayModule(projectRoot))
    }

    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        json()
    }

    val monitoringService by inject<MonitoringService>()
    monitoringService.start(this)

    monitor.subscribe(ApplicationStopped) {
        logger.info("Application stopping, closing Koin context...")
        // Koin for Ktor usually handles this, but let's be explicit if needed
    }

    configureRouting()
}
