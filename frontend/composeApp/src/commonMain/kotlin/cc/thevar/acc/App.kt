package cc.thevar.acc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.thevar.acc.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val agentVm: AgentViewModel = viewModel { AgentViewModel() }
    val systemVm: SystemViewModel = viewModel { SystemViewModel() }
    val consoleVm: ConsoleViewModel = viewModel { ConsoleViewModel() }
    
    val messages by agentVm.messages.collectAsState()
    val systemState by systemVm.state.collectAsState()
    val consoleLines by consoleVm.lines.collectAsState()

    LaunchedEffect(Unit) {
        agentVm.connect()
        systemVm.connect()
        consoleVm.connect()
    }

    AccTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("AI Command Center [acc]", fontWeight = FontWeight.Bold) },
                    actions = {
                        Text(systemState?.statusMsg ?: "Connecting...", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(if (systemState?.proxyOnline == true) "PROXY ON" else "PROXY OFF", 
                            if (systemState?.proxyOnline == true) Color.Green else Color.Gray)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing // Proper Edge-to-Edge support
        ) { padding ->
            Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Sidebar: Controls
                Sidebar(
                    systemState = systemState,
                    onCommand = { consoleVm.runCommand(it) },
                    onUpdate = { systemVm.updateSystem() }
                )
                
                // Main Content
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(modifier = Modifier.weight(0.7f).fillMaxWidth()) {
                        // Middle: Thought Stream
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                            ThoughtStream(messages)
                        }
                        
                        // Right: System Stats & Fleet
                        Box(modifier = Modifier.width(300.dp).fillMaxHeight().padding(8.dp)) {
                            SystemPanel(systemState, onCommand = { consoleVm.runCommand(it) })
                        }
                    }
                    
                    // Bottom: Console
                    Box(modifier = Modifier.weight(0.3f).fillMaxWidth().padding(8.dp)) {
                        ConsoleView(consoleLines)
                    }
                }
            }
        }
    }
}
