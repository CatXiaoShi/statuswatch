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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import luolang.status.data.NpuInfo
import luolang.status.data.Snapshot
import luolang.status.ui.components.Sparkline
import luolang.status.ui.formatFreqMhz
import luolang.status.ui.formatInt
import java.util.Locale

private val NpuNormal = Color(0xFFBA68C8)
private val NpuHot = Color(0xFFE91E63)

@Composable
fun NpuCard(npu: NpuInfo?, snapshot: Snapshot?, history: List<Float>) {
    var showHelp by remember { mutableStateOf(false) }

    val hmx = npu?.hmxPct ?: 0.0
    val accent = if (hmx > 80.0) NpuHot else NpuNormal
    val powerOn = (npu?.powerState?.uppercase() ?: "OFF") == "ON"

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
                        text = "NPU利用率",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", hmx),
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
            }

            // 状态行：HMX 电源 + 温度
            val temp = snapshot?.npuTempC() ?: snapshot?.socTempC()
            if (temp != null || npu != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // HMX 电源状态胶囊
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (powerOn) Color(0xFF43A047).copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "HMX 电源: ${if (powerOn) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (powerOn) Color(0xFF43A047)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (temp != null) {
                        Spacer(Modifier.size(8.dp))
                        TempPill(temp = temp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 次级数据 2×2 网格
            NpuStatGrid(npu = npu)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHelp = !showHelp }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "参数说明",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (showHelp) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showHelp) {
                Column(modifier = Modifier.animateContentSize()) {
                    HelpRow("HMX", "张量核心利用率（NPU 主算力，Hexagon Matrix eXtension，本卡片显示的利用率为该值）")
                    HelpRow("HVX", "向量扩展核心利用率")
                    HelpRow("Q6", "标量核心负载（KCPS）")
                    HelpRow("时钟", "CDSP 当前核心频率")
                    HelpRow("HMX电源", "NPU 张量核电源状态（ON/OFF）")
                    HelpRow("休眠计数", "子系统低功耗模式累计进入次数")
                }
            }
        }
    }
}

@Composable
private fun NpuStatGrid(npu: NpuInfo?) {
    if (npu == null || !npu.available) {
        Text(
            text = "NPU 不可用（未挂载 debugfs 或设备不支持 CDSP 监控）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val items = listOf(
        "HVX 向量" to String.format(Locale.US, "%.0f%%", npu.hvxPct),
        "Q6 标量" to String.format(Locale.US, "%.0f KCPS", npu.q6Kcps),
        "时钟" to formatFreqMhz(npu.clockKhz),
        "休眠计数" to formatInt(npu.lpmCount),
    )

    Column {
        items.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
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
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HelpRow(term: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = term,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.9f),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.6f),
        )
    }
}
