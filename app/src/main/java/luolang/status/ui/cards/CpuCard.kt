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
import androidx.compose.ui.unit.sp
import luolang.status.data.CpuCore
import luolang.status.data.Snapshot
import luolang.status.ui.compactCpus
import luolang.status.ui.components.Sparkline
import luolang.status.ui.formatFreqMhz
import java.util.Locale

private val CpuBlue = Color(0xFF3482FF)
private val CpuHot = Color(0xFFFF3B30)

@Composable
fun CpuCard(snapshot: Snapshot?, history: List<Float>) {
    val cores = snapshot?.cores.orEmpty()
    val coreLoads = snapshot?.coreLoads.orEmpty()
    val usage = snapshot?.cpuUsagePct ?: 0.0
    val temp = snapshot?.socTempC()
    val policies = snapshot?.policies.orEmpty()
    val online = snapshot?.onlineCpus ?: ""

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
                        text = "CPU 负载",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", usage),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Sparkline(
                    values = history,
                    color = CpuBlue,
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

            // 运行线程 / 阻塞 / Load average
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "运行 ${snapshot?.procsRunning ?: 0} · 阻塞 ${snapshot?.procsBlocked ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Load %.1f · %.1f · %.1f",
                        snapshot?.load1 ?: 0.0,
                        snapshot?.load5 ?: 0.0,
                        snapshot?.load15 ?: 0.0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            // 多核：负载柱 + 当前/最高频率
            cores.chunked(4).forEach { rowCores ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    rowCores.forEachIndexed { i, core ->
                        CoreCell(
                            core = core,
                            load = coreLoads.getOrNull(core.index) ?: 0f,
                        )
                    }
                    repeat(4 - rowCores.size) {
                        Box(modifier = Modifier.width(78.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // 频率簇 / 调速器
            if (policies.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                policies.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = compactCpus(p.cpus).ifEmpty { "?" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(46.dp),
                        )
                        Text(
                            text = p.governor.ifEmpty { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${p.minKhz / 1000}–${p.maxKhz / 1000} MHz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (online.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "在线核心 · $online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoreCell(core: CpuCore, load: Float) {
    val ratio = (load / 100f).coerceIn(0.02f, 1f)
    val barColor = if (load >= 80f) CpuHot else CpuBlue
    Column(
        modifier = Modifier.width(78.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "C${core.index}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = String.format(Locale.US, "%.0f%%", load),
                style = MaterialTheme.typography.bodySmall,
                color = barColor,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatFreqMhz(core.freqKhz),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (core.maxKhz > 0) {
            Text(
                text = "max ${core.maxKhz / 1000}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TempPill(temp: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = String.format(Locale.US, "%.0f°C", temp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
