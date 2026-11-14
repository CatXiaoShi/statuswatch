package luolang.status.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import luolang.status.data.Snapshot
import luolang.status.ui.components.Sparkline
import java.util.Locale

private val ChargingGreen = Color(0xFF43A047)
private val DischargingOrange = Color(0xFFFF8A00)

@Composable
fun PowerCard(snapshot: Snapshot?, history: List<Float>) {
    val battery = snapshot?.battery
    val powerW = battery?.powerW ?: 0.0
    val charging = battery?.charging == true || powerW > 0.0
    val status = battery?.let { b ->
        when {
            b.capacityPct >= 100 && b.charging -> "已充满"
            b.charging -> "充电中"
            powerW > 0.0 -> "充电中"
            else -> "放电中"
        }
    } ?: "—"

    val accent = if (charging) ChargingGreen else DischargingOrange
    val maxW = (history.maxOrNull() ?: 0f).coerceAtLeast(1f)

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "功耗",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%+.1f W", powerW),
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Sparkline(
                    values = history,
                    color = accent,
                    maxValue = maxW * 1.25f,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(48.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }
                Spacer(Modifier.size(8.dp))
                
            }
        }
    }
}
