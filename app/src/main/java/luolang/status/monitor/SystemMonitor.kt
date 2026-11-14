package luolang.status.monitor

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import luolang.status.data.DeviceInfo
import luolang.status.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 系统监控核心：定时以 root 执行采集脚本，解析并维护历史趋势。
 */
class SystemMonitor {

    /** 界面渲染的完整状态（含趋势历史）。 */
    data class State(
        val device: DeviceInfo? = null,
        val snapshot: Snapshot? = null,
        val cpuHistory: List<Float> = emptyList(),
        val memHistory: List<Float> = emptyList(),
        val npuHistory: List<Float> = emptyList(),
        val gpuHistory: List<Float> = emptyList(),
        val powerHistory: List<Float> = emptyList(),
    )

    var state by mutableStateOf(State())
        private set

    /** 注入启动时读到的静态设备信息。 */
    fun applyDevice(device: DeviceInfo) {
        state = state.copy(device = device)
    }

    private var prevCpu: luolang.status.data.CpuStat? = null
    private var prevCores: Map<Int, luolang.status.data.CpuStat> = emptyMap()
    private var prevNetRx = -1L
    private var prevNetTx = -1L
    private var prevTimeMs = 0L

    /** 读取设备静态信息（模型 / 硬件 / GPU / OpenGL / 内核），失败返回空壳。 */
    suspend fun loadDevice(): DeviceInfo = withContext(Dispatchers.IO) {
        val out = RootManager.execShell(STATIC_SCRIPT) ?: return@withContext fallbackDevice()
        var model = "unknown"
        var hardware = ""
        var gpuProp = ""
        var glesLine = ""
        for (raw in out.lineSequence()) {
            val line = raw.trim()
            val eq = line.indexOf('=')
            if (eq < 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when (key) {
                "MODEL" -> if (value.isNotEmpty()) model = value
                "HW" -> hardware = value
                "GPU" -> gpuProp = value
                "GLES" -> if (value.isNotEmpty()) glesLine = value
            }
        }

        // GLES 行示例：Qualcomm, Adreno (TM) 830, OpenGL ES 3.2 V@...
        var gpu = gpuProp
        var opengl = ""
        if (glesLine.isNotEmpty()) {
            val parts = glesLine.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 3) {
                gpu = parts[1]
                opengl = parts.drop(2).joinToString(", ")
            } else {
                opengl = glesLine
            }
        }
        DeviceInfo(
            model = model,
            hardware = hardware,
            gpu = gpu,
            opengl = opengl,
            kernel = System.getProperty("os.version") ?: "unknown",
        )
    }

    private fun fallbackDevice(): DeviceInfo {
        return DeviceInfo(
            model = Build.MODEL ?: "unknown",
            hardware = "",
            gpu = "",
            opengl = "",
            kernel = System.getProperty("os.version") ?: "unknown",
        )
    }

    /** 采集一次，更新 state（含趋势）。 */
    suspend fun collect() {
        val out = withContext(Dispatchers.IO) { RootManager.execShell(DYNAMIC_SCRIPT) }
            ?: return
        val nowMs = System.currentTimeMillis()
        val snap = ShellParser.parseDynamic(
            output = out,
            prevCpu = prevCpu,
            prevCores = prevCores,
            prevNetRx = prevNetRx,
            prevNetTx = prevNetTx,
            prevTimeMs = prevTimeMs,
            nowMs = nowMs,
        ) ?: return

        prevCpu = snap.cpuStat
        prevCores = snap.coreStats
        prevNetRx = snap.net.rxBytes
        prevNetTx = snap.net.txBytes
        prevTimeMs = nowMs

        val cpuHist = append(state.cpuHistory, snap.cpuUsagePct.toFloat())
        val memHist = append(state.memHistory, snap.mem.ramPct)
        val npuHist = append(state.npuHistory, snap.npu.hmxPct.toFloat())
        val gpuHist = append(state.gpuHistory, snap.gpu.loadPct.toFloat())
        val powerHist = append(
            state.powerHistory,
            kotlin.math.abs(snap.battery.powerW).toFloat(),
        )

        state = State(
            device = state.device,
            snapshot = snap,
            cpuHistory = cpuHist,
            memHistory = memHist,
            npuHistory = npuHist,
            gpuHistory = gpuHist,
            powerHistory = powerHist,
        )
    }

    private fun append(list: List<Float>, v: Float): List<Float> {
        val next = list + v
        return if (next.size > 60) next.takeLast(60) else next
    }

    companion object {
        /** 启动时读取一次的静态信息。 */
        val STATIC_SCRIPT = """
            set +e
            HW=${'$'}(grep -i -m1 'Hardware' /proc/cpuinfo 2>/dev/null | sed 's/.*: *//')
            echo "HW=${'$'}HW"
            MODEL=${'$'}(getprop ro.product.marketname 2>/dev/null)
            [ -z "${'$'}MODEL" ] && MODEL=${'$'}(getprop ro.product.model 2>/dev/null)
            echo "MODEL=${'$'}MODEL"
            GPU=${'$'}(getprop ro.hardware.egl 2>/dev/null)
            [ -z "${'$'}GPU" ] && GPU=${'$'}(getprop ro.gfx.driver.0 2>/dev/null)
            echo "GPU=${'$'}GPU"
            GLES=${'$'}(dumpsys SurfaceFlinger 2>/dev/null | grep -E '^GLES:' | head -n 1)
            echo "GLES=${'$'}GLES"
        """.trimIndent()

        /**
         * 每次采样执行的动态信息。
         *
         * 性能要点：逐核 / 逐热区全部用 shell 内建 `read` 读文件，避免每项 fork 一个 cat；
         * CDSP 统计文件只读一次，用单个 awk 解析，避免反复读 debugfs。
         */
        val DYNAMIC_SCRIPT = """
            set +e
            CPUS=${'$'}(awk '${'$'}1=="cpu" {for(i=2;i<=NF;i++) printf ${'$'}i" "; exit}' /proc/stat 2>/dev/null)
            echo "CPUS=${'$'}CPUS"
            CORES=${'$'}(awk '/^cpu[0-9]+ /{print ${'$'}0}' /proc/stat 2>/dev/null | tr '\n' ';')
            echo "CORES=${'$'}CORES"
            for i in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
              f=/sys/devices/system/cpu/cpu${'$'}i/cpufreq/scaling_cur_freq
              [ -f "${'$'}f" ] && { read -r v < "${'$'}f"; echo "FREQ_${'$'}i=${'$'}v"; }
              m=/sys/devices/system/cpu/cpu${'$'}i/cpufreq/cpuinfo_max_freq
              [ -f "${'$'}m" ] && { read -r mv < "${'$'}m"; echo "MAXF_${'$'}i=${'$'}mv"; }
            done
            for p in /sys/devices/system/cpu/cpufreq/policy*; do
              [ -d "${'$'}p" ] || continue
              read -r cpus < "${'$'}p/related_cpus" 2>/dev/null
              read -r gov < "${'$'}p/scaling_governor" 2>/dev/null
              read -r mn < "${'$'}p/scaling_min_freq" 2>/dev/null
              read -r mx < "${'$'}p/scaling_max_freq" 2>/dev/null
              echo "POL=${'$'}cpus|${'$'}gov|${'$'}mn|${'$'}mx"
            done
            [ -f /sys/devices/system/cpu/online ] && { read -r on < /sys/devices/system/cpu/online; echo "ONLINE=${'$'}on"; }
            read -r l1 l2 l3 _ < /proc/loadavg
            echo "LOADAVG=${'$'}l1 ${'$'}l2 ${'$'}l3"
            echo "PROCS=${'$'}(awk '/^procs_running/{r=${'$'}2} /^procs_blocked/{b=${'$'}2} END{print r, b}' /proc/stat)"
            echo "MEM=${'$'}(awk '/^MemTotal:/{t=${'$'}2} /^MemAvailable:/{a=${'$'}2} /^MemFree:/{f=${'$'}2} /^SwapTotal:/{st=${'$'}2} /^SwapFree:/{sf=${'$'}2} END{print t, a, f, st, sf}' /proc/meminfo)"
            if [ -d /sys/block/zram0 ]; then
              echo "ZRAM_ALGO=${'$'}(sed -n 's/.*\[\([^]]*\)\].*/\1/p' /sys/block/zram0/comp_algorithm 2>/dev/null)"
              ZO=""; ZC=""; ZM=""; ZE=""
              [ -f /sys/block/zram0/orig_data_size ] && read -r ZO < /sys/block/zram0/orig_data_size
              [ -f /sys/block/zram0/compr_data_size ] && read -r ZC < /sys/block/zram0/compr_data_size
              [ -f /sys/block/zram0/mem_used_total ] && read -r ZM < /sys/block/zram0/mem_used_total
              if [ -z "${'$'}ZO" ] && [ -f /sys/block/zram0/hybridswap_meminfo ]; then
                eval ${'$'}(awk '/^ZSU_O:/{print "ZO=" ${'$'}2*1024} /^ZSU_C:/{print "ZC=" ${'$'}2*1024} /^ESU_O:/{print "ZE=" ${'$'}2*1024}' /sys/block/zram0/hybridswap_meminfo)
                ZM=${'$'}ZC
              fi
              echo "ZRAM_ORIG=${'$'}ZO"
              echo "ZRAM_COMPR=${'$'}ZC"
              echo "ZRAM_MEMUSED=${'$'}ZM"
              echo "ZRAM_EXT=${'$'}ZE"
            fi
            PV=${'$'}(awk '/^pswpin/{a=${'$'}2} /^pswpout/{b=${'$'}2} END{print a, b}' /proc/vmstat)
            set -- ${'$'}PV
            echo "PSWPIN=${'$'}1"
            echo "PSWPOUT=${'$'}2"
            for z in /sys/class/thermal/thermal_zone*; do
              [ -d "${'$'}z" ] || continue
              read -r t < "${'$'}z/type" 2>/dev/null
              read -r v < "${'$'}z/temp" 2>/dev/null
              [ -n "${'$'}t" ] && [ -n "${'$'}v" ] && echo "THERM=${'$'}t@${'$'}v"
            done
            [ -f /sys/class/power_supply/battery/capacity ] && { read -r c < /sys/class/power_supply/battery/capacity; echo "BATTCAP=${'$'}c"; }
            [ -f /sys/class/power_supply/battery/temp ] && { read -r c < /sys/class/power_supply/battery/temp; echo "BATTTEMP=${'$'}c"; }
            [ -f /sys/class/power_supply/battery/power_now ] && { read -r c < /sys/class/power_supply/battery/power_now; echo "BATPWR=${'$'}c"; }
            [ -f /sys/class/power_supply/battery/voltage_now ] && { read -r c < /sys/class/power_supply/battery/voltage_now; echo "BATVOLT=${'$'}c"; }
            [ -f /sys/class/power_supply/battery/current_now ] && { read -r c < /sys/class/power_supply/battery/current_now; echo "BATCUR=${'$'}c"; }
            [ -f /sys/class/power_supply/battery/status ] && { read -r c < /sys/class/power_supply/battery/status; echo "BATSTAT=${'$'}c"; }
            { read -r u _ < /proc/uptime; echo "UPTIME=${'$'}{u%.*}"; }
            awk '{ if (${'$'}1 ~ /^(wlan0|rmnet[0-9]+|eth0|dummy0|ccmni[0-9]+|pdp[0-9]+):${'$'}/) { name=${'$'}1; sub(/:${'$'}/,"",name); print "NET_"name"=" ${'$'}2" "${'$'}10; } }' /proc/net/dev 2>/dev/null
            K=/sys/class/kgsl/kgsl-3d0
            if [ -d "${'$'}K" ]; then
              [ -r "${'$'}K/gpu_busy_percentage" ] && { read -r g < "${'$'}K/gpu_busy_percentage"; echo "GPUBUSY=${'$'}g"; }
              [ -r "${'$'}K/gpuclk" ] && { read -r g < "${'$'}K/gpuclk"; echo "GPUCLK=${'$'}g"; }
              [ -r "${'$'}K/devfreq/max_freq" ] && { read -r g < "${'$'}K/devfreq/max_freq"; echo "GPUMAX=${'$'}g"; }
              [ -r "${'$'}K/devfreq/min_freq" ] && { read -r g < "${'$'}K/devfreq/min_freq"; echo "GPUMIN=${'$'}g"; }
              [ -r "${'$'}K/devfreq/governor" ] && { read -r g < "${'$'}K/devfreq/governor"; echo "GPUGOV=${'$'}g"; }
              [ -r "${'$'}K/temp" ] && { read -r g < "${'$'}K/temp"; echo "GPUTEMP=${'$'}g"; }
              [ -r "${'$'}K/gpu_model" ] && { read -r g < "${'$'}K/gpu_model"; echo "GPUMODEL=${'$'}g"; }
            fi
            DBG=""
            for d in ${'$'}(awk '${'$'}3=="debugfs"{print ${'$'}2}' /proc/self/mounts 2>/dev/null); do
              [ -f "${'$'}d/sysmon_subsystem_stats/master_cdsp_stats" ] && { DBG="${'$'}d"; break; }
            done
            if [ -z "${'$'}DBG" ]; then
              mkdir -p /sys/kernel/debug 2>/dev/null
              mount -t debugfs debugfs /sys/kernel/debug 2>/dev/null
              [ -f /sys/kernel/debug/sysmon_subsystem_stats/master_cdsp_stats ] && DBG=/sys/kernel/debug
            fi
            STAT="${'$'}DBG/sysmon_subsystem_stats/master_cdsp_stats"
            if [ -n "${'$'}DBG" ] && [ -f "${'$'}STAT" ]; then
              awk '
                /HMX utilization/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_HMX="v }
                /HVX utilization/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_HVX="v }
                /Average Q6 load/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_Q6="v }
                /Current core clock/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_CLK="v }
                /HMX Power state/ { v=${'$'}0; sub(/.*:[ \t]*/,"",v); print "NPU_PWR="v }
                /^Count = / { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_LPM="v }
              ' "${'$'}STAT"
            fi
        """.trimIndent()
    }
}
