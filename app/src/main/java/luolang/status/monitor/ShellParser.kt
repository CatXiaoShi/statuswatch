package luolang.status.monitor

import luolang.status.data.BatteryInfo
import luolang.status.data.CpuCore
import luolang.status.data.CpuPolicy
import luolang.status.data.CpuStat
import luolang.status.data.DiskInfo
import luolang.status.data.DiskStatRaw
import luolang.status.data.GpuInfo
import luolang.status.data.MemoryInfo
import luolang.status.data.NetInfo
import luolang.status.data.NpuInfo
import luolang.status.data.PowerSettings
import luolang.status.data.Snapshot
import luolang.status.data.ThermalZone

/**
 * 把动态采集脚本的输出解析成 [Snapshot]。
 *
 * 脚本输出为一行一个 `KEY=VALUE`，解析器逐行消费，容错处理缺失字段。
 */
object ShellParser {

    fun parseDynamic(
        output: String,
        prevCpu: CpuStat?,
        prevCores: Map<Int, CpuStat> = emptyMap(),
        prevDisk: DiskStatRaw? = null,
        power: PowerSettings = PowerSettings(),
        prevNetRx: Long,
        prevNetTx: Long,
        prevTimeMs: Long,
        nowMs: Long,
    ): Snapshot? {
        if (output.isBlank()) return null

        var cpuStat: CpuStat? = null
        val cores = mutableListOf<CpuCore>()
        val coreStats = mutableMapOf<Int, CpuStat>()
        val maxFreqs = mutableMapOf<Int, Long>()
        val policies = mutableListOf<CpuPolicy>()
        var onlineCpus = ""
        var load1 = 0.0
        var load5 = 0.0
        var load15 = 0.0
        var procsRunning = 0
        var procsBlocked = 0

        var diskRaw: DiskStatRaw? = null
        var diskDevice = ""
        var diskFs = ""
        var diskTotalBytes = 0L
        var diskFreeBytes = 0L
        var diskLife = -1
        var diskEol = -1
        var diskSessionWriteMb = 0L
        var diskSessionReadMb = 0L

        var gpuBusyPct = 0.0
        var gpuBusyRaw = ""
        var gpuCurKhz = 0L
        var gpuMaxKhz = 0L
        var gpuMinKhz = 0L
        var gpuGov = ""
        var gpuTemp = 0L
        var gpuModel = ""
        var gpuAvailable = false

        var memTotal = 0L
        var memAvail = 0L
        var memFree = 0L
        var swapTotal = 0L
        var swapFree = 0L
        var zramAlgo: String? = null
        var zramOrig = 0L
        var zramCompr = 0L
        var zramMemUsed = 0L
        var zramExt = 0L
        var pswpin = 0L
        var pswpout = 0L

        var npuAvailable = false
        var hmx = 0.0
        var hvx = 0.0
        var q6 = 0.0
        var npuClk = 0L
        var npuPwr = "OFF"
        var npuLpm = 0L

        var battCap = 0
        var battTemp = 0L
        var batPowerUw = 0L
        var batVoltUv = 0L
        var batCur = 0L
        var batStatus = ""
        var uptime = 0L
        val thermals = mutableListOf<ThermalZone>()
        var netRx = 0L
        var netTx = 0L

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val eq = line.indexOf('=')
            if (eq < 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (value.isEmpty()) continue

            when {
                key == "CPUS" -> cpuStat = parseCpuStat(value)
                key == "CORES" -> {
                    value.split(';').forEach { row ->
                        val f = row.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (f.size >= 5 && f[0].startsWith("cpu")) {
                            val idx = f[0].removePrefix("cpu").toIntOrNull() ?: return@forEach
                            val stat = parseCpuStat(f.drop(1).joinToString(" ")) ?: return@forEach
                            coreStats[idx] = stat
                        }
                    }
                }
                key.startsWith("FREQ_") -> {
                    val idx = key.removePrefix("FREQ_").toIntOrNull() ?: continue
                    val khz = value.toLongOrNull() ?: continue
                    cores += CpuCore(idx, khz)
                }
                key.startsWith("MAXF_") -> {
                    val idx = key.removePrefix("MAXF_").toIntOrNull() ?: continue
                    maxFreqs[idx] = value.toLongOrNull() ?: 0L
                }
                key == "POL" -> {
                    val p = value.split('|')
                    if (p.size >= 4) {
                        policies += CpuPolicy(
                            cpus = p[0].trim(),
                            governor = p[1].trim(),
                            minKhz = p[2].trim().toLongOrNull() ?: 0L,
                            maxKhz = p[3].trim().toLongOrNull() ?: 0L,
                        )
                    }
                }
                key == "ONLINE" -> onlineCpus = value
                key == "LOADAVG" -> {
                    val p = value.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
                    if (p.size >= 3) { load1 = p[0]; load5 = p[1]; load15 = p[2] }
                }
                key == "PROCS" -> {
                    val p = value.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
                    if (p.size >= 2) { procsRunning = p[0]; procsBlocked = p[1] }
                }
                key == "GPUBUSY" -> { gpuBusyRaw = value; gpuAvailable = true }
                key == "GPULOAD" -> { gpuBusyPct = value.toDoubleOrNull() ?: gpuBusyPct; gpuAvailable = true }
                key == "GPUCLK" -> gpuCurKhz = (value.toLongOrNull() ?: 0) / 1000
                key == "GPUMAX" -> gpuMaxKhz = (value.toLongOrNull() ?: 0) / 1000
                key == "GPUMIN" -> gpuMinKhz = (value.toLongOrNull() ?: 0) / 1000
                key == "GPUGOV" -> gpuGov = value
                key == "GPUTEMP" -> gpuTemp = value.toLongOrNull() ?: 0
                key == "GPUMODEL" -> gpuModel = value
                key == "DATA" -> diskDevice = value
                key == "FSTYPE" -> diskFs = value
                key == "DSK" -> {
                    val p = value.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                    if (p.size >= 6) {
                        diskRaw = DiskStatRaw(
                            reads = p[0],
                            sectorsRead = p[1],
                            writes = p[2],
                            sectorsWritten = p[3],
                            inflight = p[4].toInt(),
                            ioTicks = p[5],
                        )
                    }
                }
                key == "STATF" -> {
                    val p = value.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                    if (p.size >= 3 && p[2] > 0) {
                        diskTotalBytes = p[0] * p[2]
                        diskFreeBytes = p[1] * p[2]
                    }
                }
                key == "UFSWRITEMB" -> diskSessionWriteMb = value.toLongOrNull() ?: 0
                key == "UFSREADMB" -> diskSessionReadMb = value.toLongOrNull() ?: 0
                key == "UFSLIFE" -> diskLife = parseHex(value)
                key == "UFSEOL" -> diskEol = parseHex(value)
                key == "MEM" -> {
                    val parts = value.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                    if (parts.size >= 5) {
                        memTotal = parts[0]; memAvail = parts[1]; memFree = parts[2]
                        swapTotal = parts[3]; swapFree = parts[4]
                    }
                }
                key == "ZRAM_ALGO" -> zramAlgo = value
                key == "ZRAM_ORIG" -> zramOrig = value.toLongOrNull() ?: 0
                key == "ZRAM_COMPR" -> zramCompr = value.toLongOrNull() ?: 0
                key == "ZRAM_MEMUSED" -> zramMemUsed = value.toLongOrNull() ?: 0
                key == "ZRAM_EXT" -> zramExt = value.toLongOrNull() ?: 0
                key == "PSWPIN" -> pswpin = value.toLongOrNull() ?: 0
                key == "PSWPOUT" -> pswpout = value.toLongOrNull() ?: 0
                key == "NPU_HMX" -> { hmx = value.toDoubleOrNull() ?: 0.0; npuAvailable = true }
                key == "NPU_AVAIL" -> if (value == "1") npuAvailable = true
                key == "NPU_HVX" -> hvx = value.toDoubleOrNull() ?: 0.0
                key == "NPU_Q6" -> q6 = value.toDoubleOrNull() ?: 0.0
                key == "NPU_CLK" -> npuClk = value.toLongOrNull() ?: 0
                key == "NPU_PWR" -> npuPwr = value
                key == "NPU_LPM" -> npuLpm = value.toLongOrNull() ?: 0
                key == "BATTCAP" -> battCap = value.toIntOrNull() ?: 0
                key == "BATTTEMP" -> battTemp = value.toLongOrNull() ?: 0
                key == "BATPWR" -> batPowerUw = value.toLongOrNull() ?: 0
                key == "BATVOLT" -> batVoltUv = value.toLongOrNull() ?: 0
                key == "BATCUR" -> batCur = value.toLongOrNull() ?: 0
                key == "BATSTAT" -> batStatus = value
                key == "UPTIME" -> uptime = value.toLongOrNull() ?: 0
                key.startsWith("THERM") -> {
                    val at = value.lastIndexOf('@')
                    if (at > 0) {
                        val type = value.substring(0, at)
                        val temp = value.substring(at + 1).toLongOrNull() ?: 0
                        thermals += ThermalZone(type, temp)
                    }
                }
                key.startsWith("NET_") -> {
                    val parts = value.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                    if (parts.size >= 2) { netRx += parts[0]; netTx += parts[1] }
                }
            }
        }

        val stat = cpuStat ?: return null

        // CPU 占用率（两次采样差值）
        val cpuUsage = computeUsage(prevCpu, stat)

        // 网速：字节差值 / 时间差 → B/s
        val dtSec = ((nowMs - prevTimeMs).coerceAtLeast(1)) / 1000.0
        val rxRate = if (prevNetRx >= 0 && netRx >= prevNetRx) {
            (netRx - prevNetRx) / dtSec
        } else 0.0
        val txRate = if (prevNetTx >= 0 && netTx >= prevNetTx) {
            (netTx - prevNetTx) / dtSec
        } else 0.0

        // 每核占用率
        val sortedCores = cores.sortedBy { it.index }
        val coreLoads = sortedCores.map { c ->
            val cur = coreStats[c.index]
            val prev = prevCores[c.index]
            if (cur != null && prev != null) computeUsage(prev, cur).toFloat() else 0f
        }

        // GPU 占用：gpu_busy_percentage 形如 "45 %"（单值即百分比）；
        // 若只有 gpubusy 的 "busy total" 则用 busy/total*100。
        if (gpuBusyPct <= 0.0 && gpuBusyRaw.isNotEmpty()) {
            val nums = Regex("""\d+(?:\.\d+)?""").findAll(gpuBusyRaw)
                .map { it.value.toDouble() }.toList()
            gpuBusyPct = when {
                nums.size >= 2 && nums[1] > 0 -> (nums[0] / nums[1] * 100.0)
                nums.size == 1 -> nums[0]
                else -> 0.0
            }.coerceIn(0.0, 100.0)
        }

        // 磁盘：扇区增量 × 512 → 字节；完成次数增量 → IOPS；io_ticks 增量 → 繁忙度
        val curDisk = diskRaw
        val diskDt = (nowMs - prevTimeMs).coerceAtLeast(1) / 1000.0
        var diskReadBps = 0.0
        var diskWriteBps = 0.0
        var diskReadIops = 0.0
        var diskWriteIops = 0.0
        var diskBusy = 0.0
        if (curDisk != null && prevDisk != null && diskDt > 0) {
            val dReadSectors = (curDisk.sectorsRead - prevDisk.sectorsRead).coerceAtLeast(0)
            val dWriteSectors = (curDisk.sectorsWritten - prevDisk.sectorsWritten).coerceAtLeast(0)
            val dReads = (curDisk.reads - prevDisk.reads).coerceAtLeast(0)
            val dWrites = (curDisk.writes - prevDisk.writes).coerceAtLeast(0)
            val dIoTicks = (curDisk.ioTicks - prevDisk.ioTicks).coerceAtLeast(0)
            diskReadBps = dReadSectors * 512.0 / diskDt
            diskWriteBps = dWriteSectors * 512.0 / diskDt
            diskReadIops = dReads / diskDt
            diskWriteIops = dWrites / diskDt
            diskBusy = (dIoTicks / (diskDt * 1000.0) * 100.0).coerceIn(0.0, 100.0)
        }

        return Snapshot(
            cpuUsagePct = cpuUsage,
            cores = sortedCores.map { c -> c.copy(maxKhz = maxFreqs[c.index] ?: c.maxKhz) },
            coreLoads = coreLoads,
            coreStats = coreStats,
            cpuStat = stat,
            policies = policies,
            onlineCpus = onlineCpus,
            load1 = load1,
            load5 = load5,
            load15 = load15,
            procsRunning = procsRunning,
            procsBlocked = procsBlocked,
            mem = MemoryInfo(
                totalKb = memTotal,
                availableKb = memAvail,
                freeKb = memFree,
                swapTotalKb = swapTotal,
                swapFreeKb = swapFree,
                zramAlgo = zramAlgo,
                zramOrigBytes = zramOrig,
                zramComprBytes = zramCompr,
                zramMemUsedBytes = zramMemUsed,
                zramExtBytes = zramExt,
                pswpinPages = pswpin,
                pswpoutPages = pswpout,
            ),
            npu = NpuInfo(
                available = npuAvailable,
                hmxPct = hmx,
                hvxPct = hvx,
                q6Kcps = q6,
                clockKhz = npuClk,
                powerState = npuPwr,
                lpmCount = npuLpm,
            ),
            gpu = GpuInfo(
                available = gpuAvailable,
                loadPct = gpuBusyPct,
                curKhz = gpuCurKhz,
                maxKhz = gpuMaxKhz,
                minKhz = gpuMinKhz,
                governor = gpuGov,
                tempMillis = gpuTemp,
                model = gpuModel,
            ),
            disk = DiskInfo(
                available = curDisk != null,
                device = diskDevice,
                fsType = diskFs,
                readBps = diskReadBps,
                writeBps = diskWriteBps,
                readIops = diskReadIops,
                writeIops = diskWriteIops,
                busyPct = diskBusy,
                inflight = curDisk?.inflight ?: 0,
                totalBytes = diskTotalBytes,
                freeBytes = diskFreeBytes,
                life = diskLife,
                eol = diskEol,
                sessionWriteBytes = diskSessionWriteMb * 1024L * 1024L,
                sessionReadBytes = diskSessionReadMb * 1024L * 1024L,
                raw = curDisk,
            ),
            battery = BatteryInfo(
                capacityPct = battCap,
                tempMillis = battTemp,
                powerW = computePowerW(batPowerUw, batVoltUv, batCur, batStatus, power),
                charging = isCharging(batStatus),
            ),
            thermalZones = thermals,
            uptimeSec = uptime,
            net = NetInfo(rxBytes = netRx, txBytes = netTx),
            netRxRateBps = rxRate,
            netTxRateBps = txRate,
        )
    }

    private fun isCharging(status: String): Boolean {
        val s = status.trim().lowercase()
        return s.startsWith("charg") || s == "full"
    }

    /**
     * 计算当前功耗（W）。充电为正、放电为负。
     *
     * 优先使用内核直接给出的 power_now；否则用电压 × 电流。
     * 厂商单位不统一：多数设备 current_now 是 µA，OPPO/一加等部分机型是 mA，
     * 因此两种解释都算一遍，取落在合理功耗区间 (0.02W ~ 25W) 的那个。
     */
    private fun computePowerW(
        powerUw: Long,
        voltUv: Long,
        cur: Long,
        status: String,
        settings: PowerSettings,
    ): Double {
        val charging = isCharging(status)
        val sign = if (charging != settings.invert) 1.0 else -1.0
        val scale = settings.scale.coerceIn(0.1f, 5.0f).toDouble()

        if (powerUw > 0) {
            return sign * powerUw / 1_000_000.0 * scale
        }
        if (voltUv <= 0 || cur == 0L) return 0.0
        val volt = voltUv / 1_000_000.0
        val c = kotlin.math.abs(cur.toDouble())

        val asMicroAmp = c / 1_000_000.0 * volt
        val asMilliAmp = c / 1_000.0 * volt
        val watt = when (settings.unit) {
            "ua" -> asMicroAmp
            "ma" -> asMilliAmp
            else -> when {
                asMicroAmp in 0.02..25.0 -> asMicroAmp
                asMilliAmp in 0.02..25.0 -> asMilliAmp
                else -> asMicroAmp
            }
        }
        return sign * watt * scale
    }

    /** 解析 "0x01" 或 "1" 形式的十六进制/十进制整数。 */
    private fun parseHex(value: String): Int {
        val v = value.trim()
        val hex = v.removePrefix("0x").removePrefix("0X")
        return hex.toIntOrNull(16) ?: v.toIntOrNull() ?: -1
    }


    private fun parseCpuStat(value: String): CpuStat? {
        val p = value.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (p.size < 4) return null
        return CpuStat(
            user = p.getOrElse(0) { 0 },
            nice = p.getOrElse(1) { 0 },
            system = p.getOrElse(2) { 0 },
            idle = p.getOrElse(3) { 0 },
            iowait = p.getOrElse(4) { 0 },
            irq = p.getOrElse(5) { 0 },
            softirq = p.getOrElse(6) { 0 },
            steal = p.getOrElse(7) { 0 },
        )
    }

    private fun computeUsage(prev: CpuStat?, cur: CpuStat): Double {
        if (prev == null) return 0.0
        val dTotal = cur.total - prev.total
        val dIdle = cur.idleTotal - prev.idleTotal
        if (dTotal <= 0) return 0.0
        val usage = (dTotal - dIdle).toDouble() / dTotal * 100.0
        return usage.coerceIn(0.0, 100.0)
    }
}
