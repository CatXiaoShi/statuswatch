package luolang.status.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import luolang.status.data.Snapshot
import luolang.status.ui.formatUptime
import java.util.Locale

@Composable
fun FooterCard(snapshot: Snapshot?) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FooterMetric(
                label = "开机时间",
                value = formatUptime(snapshot?.uptimeSec ?: 0),
            )
            FooterMetric(
                label = "电池温度",
                value = snapshot?.batteryTempC()?.let {
                    String.format(Locale.US, "%.1f°C", it)
                } ?: "—",
            )
            FooterMetric(
                label = "电量",
                value = "${snapshot?.battery?.capacityPct ?: 0}%",
            )
        }
    }
}

@Composable
private fun FooterMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
