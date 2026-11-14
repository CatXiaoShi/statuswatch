package luolang.status.data

/**
 * 设备静态信息（启动时读取一次）。
 */
data class DeviceInfo(
    val model: String,
    val hardware: String,
    val gpu: String,
    val opengl: String,
    val kernel: String,
)

/**
 * 单个 CPU 核心的当前频率与最高频率（单位 kHz）。
 */
data class CpuCore(
    val index: Int,
    val freqKhz: Long,
    val maxKhz: Long = 0L,
)

/** 一个 cpufreq policy（频率簇）。 */
data class CpuPolicy(
    val cpus: String,
    val governor: String,
    val minKhz: Long,
    val maxKhz: Long,
)

/** GPU（Adreno kgsl）信息。 */
data class GpuInfo(
    val available: Boolean = false,
    val loadPct: Double = 0.0,
    val curKhz: Long = 0L,
    val maxKhz: Long = 0L,
    val minKhz: Long = 0L,
    val governor: String = "",
    val tempMillis: Long = 0L,
    val model: String = "",
)

/**
 * /proc/stat 第一行（cpu 总计）的原始累计值，用于两次采样间计算占用率。
 */
data class CpuStat(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long,
) {
    val total: Long get() = user + nice + system + idle + iowait + irq + softirq + steal
    val idleTotal: Long get() = idle + iowait
}

/** 热区（type 为内核命名，temp 单位为 0.001 °C）。 */
data class ThermalZone(
    val type: String,
    val tempMillis: Long,
)

data class MemoryInfo(
    val totalKb: Long,
    val availableKb: Long,
    val freeKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val zramAlgo: String?,
    val zramOrigBytes: Long,
    val zramComprBytes: Long,
    val zramMemUsedBytes: Long,
    val zramExtBytes: Long,
    val pswpinPages: Long,
    val pswpoutPages: Long,
) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0)
    val ramPct: Float get() = if (totalKb > 0) usedKb.toFloat() / totalKb * 100f else 0f
    val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
    val swapPct: Float get() = if (swapTotalKb > 0) swapUsedKb.toFloat() / swapTotalKb * 100f else 0f
    /** 压缩率：压缩后 / 原始，越小压缩效果越好。 */
    val zramRatioPct: Float
        get() = if (zramOrigBytes > 0) zramComprBytes.toFloat() / zramOrigBytes * 100f else 0f
}

data class NpuInfo(
    val available: Boolean,
    val hmxPct: Double,
    val hvxPct: Double,
    val q6Kcps: Double,
    val clockKhz: Long,
    val powerState: String,
    val lpmCount: Long,
)

data class BatteryInfo(
    val capacityPct: Int,
    val tempMillis: Long,
    /** 当前功耗（W）。充电为正，放电为负。 */
    val powerW: Double = 0.0,
    val charging: Boolean = false,
)

data class NetInfo(
    val rxBytes: Long,
    val txBytes: Long,
)

/**
 * 一次采样得到的完整快照。
 */
data class Snapshot(
    val cpuUsagePct: Double,
    val cores: List<CpuCore>,
    /** 每核占用率（与 cores 的 index 对齐）。 */
    val coreLoads: List<Float> = emptyList(),
    /** 每核原始累计值，供下一次采样计算增量。 */
    val coreStats: Map<Int, CpuStat> = emptyMap(),
    val cpuStat: CpuStat,
    val policies: List<CpuPolicy> = emptyList(),
    val onlineCpus: String = "",
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    val procsRunning: Int = 0,
    val procsBlocked: Int = 0,
    val mem: MemoryInfo,
    val npu: NpuInfo,
    val gpu: GpuInfo = GpuInfo(),
    val battery: BatteryInfo,
    val thermalZones: List<ThermalZone>,
    val uptimeSec: Long,
    val net: NetInfo,
    val netRxRateBps: Double,
    val netTxRateBps: Double,
) {
    /** 挑选 SoC/CPU 相关热区温度（°C），跳过读数为 0 的 cpuss 等无效传感器。 */
    fun socTempC(): Float? {
        val valid = thermalZones.filter { it.tempMillis > 0 }
        val cpuZones = valid.filter { it.type.lowercase().startsWith("cpu-") }
        val key = cpuZones.maxByOrNull { it.tempMillis }
            ?: valid.firstOrNull {
                val t = it.type.lowercase()
                t.contains("soc") || t.contains("cpu") || t.contains("tsens") ||
                    t.contains("mtkts") || t.contains("quiet")
            }
            ?: valid.firstOrNull()
        return key?.let { it.tempMillis / 1000f }
    }

    /** GPU 温度（°C）：优先 kgsl 自带 temp，其次 gpuss 热区。 */
    fun gpuTempC(): Float? {
        if (gpu.tempMillis > 0) return gpu.tempMillis / 1000f
        val key = thermalZones.filter { it.tempMillis > 0 }
            .firstOrNull { it.type.lowercase().contains("gpuss") || it.type.lowercase().contains("gpu") }
        return key?.let { it.tempMillis / 1000f }
    }

    fun npuTempC(): Float? {
        val key = thermalZones.filter { it.tempMillis > 0 }.firstOrNull {
            val t = it.type.lowercase()
            t.contains("npu") || t.contains("nsp") || t.contains("cdsp") || t.contains("aie")
        }
        return key?.let { it.tempMillis / 1000f }
    }

    fun batteryTempC(): Float? = if (battery.tempMillis > 0) battery.tempMillis / 10f else null
}
