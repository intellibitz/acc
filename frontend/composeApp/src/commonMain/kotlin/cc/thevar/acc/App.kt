package cc.thevar.acc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource

import cc.thevar.acc.shared.Res
import cc.thevar.acc.shared.compose_multiplatform
import cc.thevar.acc.protocol.AgentMessage
import cc.thevar.acc.protocol.AgentStatus
import cc.thevar.acc.ui.AgentViewModel

@Composable
@Preview
fun App() {
    val viewModel: AgentViewModel = viewModel { AgentViewModel() }
    val messages by viewModel.messages.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Acc Thought Stream",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            ThoughtStream(messages)
        }
    }
}

@Composable
fun ThoughtStream(messages: List<AgentMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages) { message ->
            AgentMessageCard(message)
        }
    }
}

@Composable
fun AgentMessageCard(message: AgentMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (message.status) {
                AgentStatus.THINKING -> MaterialTheme.colorScheme.secondaryContainer
                AgentStatus.EXECUTING -> MaterialTheme.colorScheme.tertiaryContainer
                AgentStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message.agentName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                StatusBadge(message.status)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message.content)

            if (message.thoughts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Internal Reasoning:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                message.thoughts.forEach { thought ->
                    Text(
                        text = "• ${thought.summary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (message.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Actions Taken:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                message.actions.forEach { action ->
                    ToolActionItem(action)
                }
            }
        }
    }
}

@Composable
fun ToolActionItem(action: cc.thevar.acc.protocol.AgentAction) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = Color.Black.copy(alpha = 0.05f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "> ${action.tool}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = action.input,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
            action.result?.let { result ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: AgentStatus) {
    val color = when (status) {
        AgentStatus.IDLE -> Color.Gray
        AgentStatus.THINKING -> Color.Blue
        AgentStatus.EXECUTING -> Color.Green
        AgentStatus.ERROR -> Color.Red
        AgentStatus.DONE -> Color.Magenta
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.name,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
