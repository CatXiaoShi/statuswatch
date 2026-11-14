package luolang.status.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import luolang.status.data.DeviceInfo
import luolang.status.data.Snapshot
import luolang.status.ui.formatRate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HeaderCard(device: DeviceInfo?, snapshot: Snapshot?) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val hw = device?.hardware?.takeIf { it.isNotBlank() } ?: device?.model ?: "未知"
                    val gpu = device?.gpu?.takeIf { it.isNotBlank() } ?: "—"
                    val gl = device?.opengl?.takeIf { it.isNotBlank() }
                        ?.lineSequence()?.firstOrNull()?.trim() ?: "—"
                    Text(
                        text = "CPU · $hw",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "GPU · $gpu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "OpenGL · $gl",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.padding(horizontal = 8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    val rx = snapshot?.netRxRateBps ?: 0.0
                    val tx = snapshot?.netTxRateBps ?: 0.0
                    Text(
                        text = "↓ ${formatRate(rx)}  ↑ ${formatRate(tx)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    val cap = snapshot?.battery?.capacityPct ?: 0
                    val bt = snapshot?.batteryTempC()
                    Text(
                        text = if (bt != null) "电量 $cap% · $bt°C"
                        else "电量 $cap%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
