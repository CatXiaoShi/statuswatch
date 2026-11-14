package luolang.status.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import luolang.status.monitor.SystemMonitor
import luolang.status.ui.cards.CpuCard
import luolang.status.ui.cards.FooterCard
import luolang.status.ui.cards.GpuCard
import luolang.status.ui.cards.HeaderCard
import luolang.status.ui.cards.MemoryCard
import luolang.status.ui.cards.NpuCard
import luolang.status.ui.cards.PowerCard
import java.util.Locale

/** 可选采样间隔（毫秒）：0.1s ~ 5s。 */
val SAMPLE_INTERVALS = listOf(100, 200, 500, 1000, 2000, 5000)

fun formatInterval(ms: Int): String = String.format(Locale.US, "%.1fs", ms / 1000.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: SystemMonitor.State,
    intervalMs: Int,
    onIntervalChange: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val snapshot = state.snapshot

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "洛水流汐",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { showAbout = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "关于",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box {
                        Row(
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { menuOpen = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "采样 ${formatInterval(intervalMs)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            SAMPLE_INTERVALS.forEach { ms ->
                                DropdownMenuItem(
                                    text = { Text(formatInterval(ms)) },
                                    onClick = {
                                        onIntervalChange(ms)
                                        menuOpen = false
                                    },
                                    trailingIcon = {
                                        if (ms == intervalMs) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            HeaderCard(device = state.device, snapshot = snapshot)

            Spacer(Modifier.height(12.dp))
            CpuCard(snapshot = snapshot, history = state.cpuHistory)

            Spacer(Modifier.height(12.dp))
            GpuCard(snapshot = snapshot, history = state.gpuHistory)

            Spacer(Modifier.height(12.dp))
            MemoryCard(mem = snapshot?.mem, history = state.memHistory)

            Spacer(Modifier.height(12.dp))
            NpuCard(npu = snapshot?.npu, snapshot = snapshot, history = state.npuHistory)

            Spacer(Modifier.height(12.dp))
            PowerCard(snapshot = snapshot, history = state.powerHistory)

            Spacer(Modifier.height(12.dp))
            FooterCard(snapshot = snapshot)

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}
