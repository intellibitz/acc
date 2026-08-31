package cc.thevar.acc.service

import cc.thevar.acc.protocol.ModelManifest
import cc.thevar.acc.protocol.ProvisioningStage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProvisioningServiceTest {
    private val projectRoot = File("build/test-root").apply { mkdirs() }
    private val fleetManager = mockk<FleetManager>()
    private val service = ProvisioningService(projectRoot, fleetManager)

    @Test
    fun `test autoScale detection`() {
        runBlocking {
            // This just verifies it doesn't crash as it's mostly logs for now
            service.autoScale()
            assertNotNull(service)
        }
    }

    @Test
    fun `test calculateGpuLayers`() {
        val method = service.javaClass.getDeclaredMethod("calculateGpuLayers", String::class.java)
        method.isAccessible = true
        
        assertEquals(20, method.invoke(service, "llama3-70b"))
        assertEquals(10, method.invoke(service, "command-r-plus"))
        assertEquals(99, method.invoke(service, "phi3-mini"))
    }
}
