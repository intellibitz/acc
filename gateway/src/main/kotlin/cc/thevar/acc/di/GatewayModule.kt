package cc.thevar.acc.di

import cc.thevar.acc.service.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.io.File

fun gatewayModule(projectRoot: File) = module {
    single { projectRoot }
    single { SessionManager() }
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single { FleetManager(File(get<File>(), "config")) }
    single { ProvisioningService(get(), get(), get()) }
    single { SupervisorService(get()) }
    single { CommandHandler(get(), get()) }
    single { MonitoringService(get(), get(), get(), get()) }
}
