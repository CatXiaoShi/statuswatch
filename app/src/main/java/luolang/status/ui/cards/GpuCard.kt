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
import androidx.compose.foundation.layout.width
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
import luolang.status.ui.formatFreqMhz
import java.util.Locale

private val GpuTeal = Color(0xFF00B3A4)
private val GpuHot = Color(0xFFFF3B30)

@Composable
fun GpuCard(snapshot: Snapshot?, history: List<Float>) {
    val gpu = snapshot?.gpu
    val load = gpu?.loadPct ?: 0.0
    val temp = snapshot?.gpuTempC()
    val accent = if (load >= 80.0) GpuHot else GpuTeal

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
                        text = "GPU 负载",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", load),
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Sparkline(
                    values = history,
                    color = accent,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(48.dp),
                )
                if (temp != null) {
                    Spacer(Modifier.width(8.dp))
                    TempPill(temp = temp)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (gpu == null || !gpu.available) {
                Text(
                    text = "GPU 不可用（未找到 kgsl 节点或设备不支持）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                GpuStat("当前频率", formatFreqMhz(gpu.curKhz))
                GpuStat("最高", formatFreqMhz(gpu.maxKhz))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                GpuStat("最低", formatFreqMhz(gpu.minKhz))
                GpuStat("调速器", gpu.governor.ifEmpty { "—" })
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "型号 ${gpu.model.ifEmpty { "—" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GpuStat(label: String, value: String) {
    Column(modifier = Modifier.width(150.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
