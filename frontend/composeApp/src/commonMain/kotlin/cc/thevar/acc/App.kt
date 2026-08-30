package cc.thevar.acc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.thevar.acc.protocol.*
import cc.thevar.acc.ui.AgentViewModel
import cc.thevar.acc.ui.ConsoleViewModel
import cc.thevar.acc.ui.SystemViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
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

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFBB86FC),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI Command Center [acc]", fontWeight = FontWeight.Bold) },
                    actions = {
                        StatusBadge(if (systemState?.proxyOnline == true) "PROXY ON" else "PROXY OFF", 
                            if (systemState?.proxyOnline == true) Color.Green else Color.Gray)
                    }
                )
            }
        ) { padding ->
            Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Sidebar: Controls
                Sidebar(
                    systemState = systemState,
                    onCommand = { consoleVm.runCommand(it) }
                )
                
                // Main Content: Split Middle and Right
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

@Composable
fun Sidebar(systemState: SystemState?, onCommand: (String) -> Unit) {
    Column(
        modifier = Modifier.width(200.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("CONTROLS", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        
        systemState?.partialDownloads?.takeIf { it.isNotEmpty() }?.let { partials ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Partial Downloads:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Yellow)
                    partials.forEach { 
                        Text("• $it", fontSize = 9.sp, color = Color.LightGray)
                    }
                    Button(
                        onClick = { onCommand("up") },
                        modifier = Modifier.fillMaxWidth().height(24.dp).padding(top = 4.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.2f), contentColor = Color.Yellow)
                    ) {
                        Text("Resume All", fontSize = 9.sp)
                    }
                }
            }
        }

        ControlButton("Provision (UP)", Icons.Default.CloudDownload, Color.Green) { onCommand("up") }
        ControlButton("Hardware Tune", Icons.Default.SettingsSuggest, Color(0xFFBB86FC)) { onCommand("tune-hw") }
        ControlButton("Sync Service", Icons.Default.Sync, Color.Cyan) { onCommand("sync") }
        ControlButton("Auto-Scale", Icons.Default.AutoGraph, Color.Green) { onCommand("auto-scale") }
        ControlButton("Benchmark", Icons.Default.Speed, Color.Yellow) { onCommand("benchmark") }
        ControlButton("Prune Models", Icons.Default.DeleteSweep, Color.Red) { onCommand("prune") }
        
        Spacer(modifier = Modifier.weight(1f))
        
        ControlButton("Stop All", Icons.Default.Stop, Color.Red) { onCommand("stop") }
    }
}

@Composable
fun ControlButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f), contentColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ThoughtStream(messages: List<AgentMessage>) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    
    Column {
        Text("AGENT THOUGHT STREAM", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                AgentMessageCard(message)
            }
        }
    }
}

@Composable
fun ConsoleView(lines: List<ConsoleLine>) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Surface(
        color = Color.Black,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("SYSTEM CONSOLE", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    Text(
                        text = line.text,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = when(line.type) {
                            "COMMAND" -> Color.Cyan
                            "ERROR" -> Color.Red
                            "SUCCESS" -> Color.Green
                            else -> Color.LightGray
                        },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SystemPanel(state: SystemState?, onCommand: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("HARDWARE STATUS", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        
        state?.stats?.let { stats ->
            StatItem("CPU", "${stats.cpuUtilization}%", stats.cpuUtilization / 100f)
            StatItem("RAM", "${stats.ramUsed.toInt()}G / ${stats.ramTotal.toInt()}G", stats.ramUsed / stats.ramTotal)
            if (stats.gpu.active) {
                StatItem("GPU", "${stats.gpu.utilization}%", stats.gpu.utilization / 100f)
                Text("VRAM: ${stats.gpu.memoryUsed.toInt()}MB / ${stats.gpu.memoryTotal.toInt()}MB", fontSize = 10.sp, color = Color.Gray)
                Text("Temp: ${stats.gpu.temperature}°C | Pwr: ${stats.gpu.power}W", fontSize = 10.sp, color = Color.Gray)
            }
            Text("Disk Usage: ${stats.diskUsage}", fontSize = 11.sp, color = Color.LightGray)
        } ?: Text("Loading stats...", color = Color.DarkGray)

        Spacer(modifier = Modifier.height(8.dp))
        Text("FLEET STATUS", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(0.6f)) {
            state?.fleet?.let { fleet ->
                items(fleet) { model ->
                    FleetItem(model, onProvision = { onCommand("up $it") })
                }
            }
        }

        state?.partialDownloads?.takeIf { it.isNotEmpty() }?.let { partials ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PARTIAL DOWNLOADS", style = MaterialTheme.typography.labelLarge, color = Color.Yellow)
                Spacer(modifier = Modifier.weight(1f))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(0.4f)) {
                items(partials) { name ->
                    PartialDownloadItem(name, onResume = { onCommand("up $it") })
                }
            }
        }
    }
}

@Composable
fun PartialDownloadItem(name: String, onResume: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Icon(Icons.Default.Pending, null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, fontSize = 11.sp, color = Color.White, modifier = Modifier.weight(1f))
        
        IconButton(
            onClick = { onResume(name) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
        }
        
        Text("PARTIAL", fontSize = 9.sp, color = Color.Yellow)
    }
}

@Composable
fun StatItem(label: String, value: String, progress: Float) {
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (progress > 0.8f) Color.Red else MaterialTheme.colorScheme.secondary,
            trackColor = Color.DarkGray
        )
    }
}

@Composable
fun FleetItem(model: ModelStatus, onProvision: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (model.isRunning) Color.Green else Color.DarkGray))
        Spacer(modifier = Modifier.width(8.dp))
        Text(model.name, fontSize = 12.sp, color = if (model.isInstalled) Color.White else Color.Gray, modifier = Modifier.weight(1f))
        
        if (!model.isInstalled) {
            IconButton(
                onClick = { onProvision(model.name) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Download, null, tint = Color.Green, modifier = Modifier.size(16.dp))
            }
        }
        
        Text(model.type, fontSize = 10.sp, color = if (model.type == "PRIV") Color.Magenta else Color.Cyan, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun AgentMessageCard(message: AgentMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (message.status) {
                AgentStatus.THINKING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                AgentStatus.EXECUTING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                AgentStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.agentName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.weight(1f))
                StatusBadge(message.status.name, when(message.status) {
                    AgentStatus.THINKING -> Color.Blue
                    AgentStatus.EXECUTING -> Color.Green
                    AgentStatus.ERROR -> Color.Red
                    AgentStatus.DONE -> Color.Magenta
                    else -> Color.Gray
                })
            }
            Text(message.content, modifier = Modifier.padding(vertical = 4.dp))
            
            message.thoughts.forEach { thought ->
                Text("• ${thought.summary}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            message.actions.forEach { action ->
                ToolActionItem(action)
            }
        }
    }
}

@Composable
fun ToolActionItem(action: AgentAction) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("> ${action.tool} ${action.input}", fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color.Cyan)
            action.result?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.DarkGray)
                Text(it, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
