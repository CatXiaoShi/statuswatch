package luolang.status.ui.cards

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import luolang.status.data.BrandNames
import luolang.status.data.DeviceInfo
import luolang.status.data.DiskInfo
import luolang.status.data.Snapshot
import luolang.status.ui.components.DualSparkline
import luolang.status.ui.formatBytes
import java.util.Locale

private val ReadBlue = Color(0xFF3482FF)
private val WriteOrange = Color(0xFFFF8A00)
private val HealthGreen = Color(0xFF43A047)
private val HealthWarn = Color(0xFFFF8A00)
private val HealthBad = Color(0xFFFF3B30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiskCard(
    disk: DiskInfo?,
    device: DeviceInfo?,
    snapshot: Snapshot?,
    readHistory: List<Float>,
    writeHistory: List<Float>,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val readMbps = (disk?.readBps ?: 0.0) / 1048576.0
    val writeMbps = (disk?.writeBps ?: 0.0) / 1048576.0
    val health = healthState(disk)

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.clickable { showSheet = true },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "存储 I/O",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "读 ${formatSpeed(readMbps)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = ReadBlue,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "写 ${formatSpeed(writeMbps)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WriteOrange,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                DualSparkline(
                    readValues = readHistory,
                    writeValues = writeHistory,
                    readColor = ReadBlue,
                    writeColor = WriteOrange,
                    modifier = Modifier.weight(1.6f).height(56.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SmallStat("IOPS 读", String.format(Locale.US, "%.0f", disk?.readIops ?: 0.0))
                SmallStat("IOPS 写", String.format(Locale.US, "%.0f", disk?.writeIops ?: 0.0))
                SmallStat("繁忙", String.format(Locale.US, "%.0f%%", disk?.busyPct ?: 0.0))
                SmallStat("队列", "${disk?.inflight ?: 0}")
            }

            Spacer(Modifier.height(10.dp))

            // 容量
            val used = disk?.usedBytes ?: 0L
            val total = disk?.totalBytes ?: 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((disk?.usedPct ?: 0f).coerceIn(0f, 100f) / 100f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ReadBlue),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${formatBytes(used)} / ${formatBytes(total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append(disk?.fsType?.ifEmpty { "—" } ?: "—")
                        append(" · ")
                        append(BrandNames.display(device?.diskVendor ?: ""))
                        val m = device?.diskModel ?: ""
                        if (m.isNotEmpty()) append(" $m")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (disk != null && disk.available) {
                    Pill(
                        text = health.label,
                        color = health.color,
                    )
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            DiskDetail(disk = disk, device = device, snapshot = snapshot)
        }
    }
}

private data class HealthState(val label: String, val color: Color)

private fun healthState(disk: DiskInfo?): HealthState {
    if (disk == null || !disk.available) return HealthState("—", Color(0xFF8A8A8E))
    return when (disk.eol) {
        2 -> HealthState("UFS 警告", HealthWarn)
        3 -> HealthState("UFS 紧急", HealthBad)
        1 -> HealthState("UFS 正常", HealthGreen)
        else -> if (disk.life > 0) HealthState("UFS 正常", HealthGreen)
        else HealthState("未知", Color(0xFF8A8A8E))
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Column {
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

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun DiskDetail(disk: DiskInfo?, device: DeviceInfo?, snapshot: Snapshot?) {
    val temp = snapshot?.diskTempC()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = "存储详情",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        DetailRow("设备型号", device?.diskModel?.ifEmpty { "—" } ?: "—")
        DetailRow("制造商", BrandNames.display(device?.diskVendor ?: ""))
        DetailRow("文件系统", "${disk?.fsType ?: "—"}（${disk?.device ?: "—"}）")
        DetailRow(
            "容量",
            if ((disk?.totalBytes ?: 0) > 0) {
                "${formatBytes(disk!!.usedBytes)} 已用 / ${formatBytes(disk.totalBytes)}"
            } else "—",
        )
        DetailRow("UFS 寿命估计", lifeText(disk?.life ?: -1))
        DetailRow("UFS 健康状态", eolText(disk?.eol ?: -1))
        DetailRow(
            "本次开机写入",
            if ((disk?.sessionWriteBytes ?: 0) > 0) formatBytes(disk!!.sessionWriteBytes)
            else "未提供",
        )
        DetailRow(
            "本次开机读取",
            if ((disk?.sessionReadBytes ?: 0) > 0) formatBytes(disk!!.sessionReadBytes)
            else "未提供",
        )
        DetailRow("温度", temp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "设备未暴露")

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(10.dp))
        Text(
            text = "数据来自 /proc/diskstats、UFS health_descriptor、F2FS/EXT4 lifetime_write_kbytes，" +
                "部分设备可能不提供寿命与温度信息。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun lifeText(life: Int): String = when (life) {
    in 1..10 -> "0x0${life.toString(16)} · 损耗约 ${(life - 1) * 10}%–${life * 10}%"
    11 -> "0x0B · 损耗 > 100%"
    else -> "未提供"
}

/** 速度文本：≥100 MB/s 时省去小数，避免换行。 */
private fun formatSpeed(mbps: Double): String =
    if (mbps >= 100.0) {
        String.format(Locale.US, "%.0f MB/s", mbps)
    } else {
        String.format(Locale.US, "%.1f MB/s", mbps)
    }

private fun eolText(eol: Int): String = when (eol) {
    1 -> "0x01 · 正常"
    2 -> "0x02 · 警告"
    3 -> "0x03 · 紧急"
    else -> "未提供"
}
