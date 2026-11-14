package luolang.status.ui

import java.util.Locale

/** 字节转人类可读（自动 KB/MB/GB/TB）。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    var v = bytes.toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var u = 0
    while (v >= 1024 && u < units.lastIndex) {
        v /= 1024.0
        u++
    }
    return if (u == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", v, units[u])
}

/** KB 转人类可读。 */
fun formatKb(kb: Long): String = formatBytes(kb * 1024)

/** kHz 频率转 MHz 显示。 */
fun formatFreqMhz(khz: Long): String {
    if (khz <= 0) return "—"
    return if (khz % 1000 == 0L) "${khz / 1000} MHz"
    else String.format(Locale.US, "%.0f MHz", khz / 1000.0)
}

/** 页数 → 字节（每页 4KB）。 */
fun pagesToBytes(pages: Long): Long = pages * 4096L

/** 秒转可读时长。 */
fun formatUptime(sec: Long): String {
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        d > 0 -> "${d}天 ${h}时 ${m}分"
        h > 0 -> "${h}时 ${m}分 ${s}秒"
        m > 0 -> "${m}分 ${s}秒"
        else -> "${s}秒"
    }
}

/** 网速转人类可读。 */
fun formatRate(bps: Double): String {
    if (bps < 0) return "—"
    var v = bps
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var u = 0
    while (v >= 1024 && u < units.lastIndex) {
        v /= 1024.0
        u++
    }
    return String.format(Locale.US, "%.1f %s", v, units[u])
}

/** 千分位整数。 */
fun formatInt(n: Long): String = String.format(Locale.US, "%,d", n)

/** 把 "0 1 2 3 4 5" 压缩成 "0-5"，不连续则用逗号。 */
fun compactCpus(raw: String): String {
    val nums = raw.trim().split(Regex("\\s+"))
        .mapNotNull { it.toIntOrNull() }
        .distinct()
        .sorted()
    if (nums.isEmpty()) return raw
    val parts = mutableListOf<String>()
    var start = nums.first()
    var prev = start
    for (n in nums.drop(1)) {
        if (n == prev + 1) {
            prev = n
        } else {
            parts += if (start == prev) "$start" else "$start-$prev"
            start = n
            prev = n
        }
    }
    parts += if (start == prev) "$start" else "$start-$prev"
    return parts.joinToString(",")
}
