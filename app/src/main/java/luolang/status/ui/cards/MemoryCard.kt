package luolang.status.ui.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import luolang.status.data.MemoryInfo
import luolang.status.ui.components.Sparkline
import luolang.status.ui.formatBytes
import luolang.status.ui.formatKb
import luolang.status.ui.pagesToBytes
import java.util.Locale

@Composable
fun MemoryCard(mem: MemoryInfo?, history: List<Float>) {
    var expanded by remember { mutableStateOf(false) }

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
                        text = "内存占用",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", mem?.ramPct ?: 0f),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Sparkline(
                    values = history,
                    color = Color(0xFF4FC3F7),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // 物理内存 / 交换进度条
            if (mem != null) {
                BarRow(
                    label = "物理",
                    usedText = formatKb(mem.usedKb),
                    totalText = formatKb(mem.totalKb),
                    pct = mem.ramPct,
                    color = Color(0xFF64B5F6),
                )
                Spacer(Modifier.height(8.dp))
                BarRow(
                    label = "Swap",
                    usedText = formatKb(mem.swapUsedKb),
                    totalText = formatKb(mem.swapTotalKb),
                    pct = mem.swapPct,
                    color = Color(0xFFFFB74D),
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            Spacer(Modifier.height(4.dp))

            // 折叠区：ZRAM 与 SWAP IO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ZRAM 状态${mem?.zramAlgo?.let { " ($it)" } ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "展开/收起",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded && mem != null) {
                Column(modifier = Modifier.animateContentSize()) {
                    if (mem.zramAlgo != null || mem.zramOrigBytes > 0) {
                        ZramDetail(mem = mem)
                        Spacer(Modifier.height(12.dp))
                    }
                    SwapIoDetail(mem = mem)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "拓展内存 · 回写块已使用: ${formatBytes(mem.zramExtBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarRow(label: String, usedText: String, totalText: String, pct: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct.coerceIn(0f, 100f) / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = "$usedText / $totalText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ZramDetail(mem: MemoryInfo) {
    Column {
        if (mem.zramOrigBytes <= 0L) {
            Text(
                text = "该设备未开放 ZRAM 压缩统计节点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val ratio = mem.zramRatioPct
        val ratioColor = if (ratio <= 50f) Color(0xFF43A047) else MaterialTheme.colorScheme.error
        Text(
            text = "原始数据 ${formatBytes(mem.zramOrigBytes)} → 压缩后 ${formatBytes(mem.zramComprBytes)} → 物理占用 ${formatBytes(mem.zramMemUsedBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "压缩率 ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ratioColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", ratio),
                    style = MaterialTheme.typography.labelLarge,
                    color = ratioColor,
                )
            }
        }
    }
}

@Composable
private fun SwapIoDetail(mem: MemoryInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = " 读出 ${formatBytes(pagesToBytes(mem.pswpinPages))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "写入 ${formatBytes(pagesToBytes(mem.pswpoutPages))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
