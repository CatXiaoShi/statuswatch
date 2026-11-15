package luolang.status.monitor

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import luolang.status.data.DeviceInfo
import luolang.status.data.DiskStatRaw
import luolang.status.data.PowerSettings
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
        val diskReadHistory: List<Float> = emptyList(),
        val diskWriteHistory: List<Float> = emptyList(),
        val powerHistory: List<Float> = emptyList(),
    )

    var state by mutableStateOf(State())
        private set

    /** 功耗校准设置，由界面写入。 */
    var powerSettings: PowerSettings = PowerSettings()

    /** 注入启动时读到的静态设备信息。 */
    fun applyDevice(device: DeviceInfo) {
        state = state.copy(device = device)
    }

    private var prevCpu: luolang.status.data.CpuStat? = null
    private var prevCores: Map<Int, luolang.status.data.CpuStat> = emptyMap()
    private var prevDisk: DiskStatRaw? = null
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
        var diskModel = ""
        var diskVendor = ""
        var ramModel = ""
        var ramVendor = ""
        var ramType = ""
        var ramInfo = ""
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
                "DISKMODEL" -> diskModel = value
                "DISKVENDOR" -> diskVendor = value
                "RAMMODEL" -> ramModel = value
                "RAMVENDOR" -> ramVendor = value
                "RAMTYPE" -> ramType = value
                "RAMINFO" -> ramInfo = value
            }
        }

        // /proc/devinfo/ddr 格式：Device manufacture: Samsung|D1b|16G
        if (ramInfo.isNotEmpty()) {
            val parts = ramInfo.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                ramVendor = parts[0]
                ramModel = parts.drop(1).joinToString(" ")
            } else if (parts.size == 1) {
                ramVendor = parts[0]
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
            diskModel = diskModel.trim(),
            diskVendor = diskVendor.trim(),
            ramModel = ramModel.trim(),
            ramVendor = ramVendor.trim(),
            ramType = ramType.trim(),
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
            prevDisk = prevDisk,
            power = powerSettings,
            prevNetRx = prevNetRx,
            prevNetTx = prevNetTx,
            prevTimeMs = prevTimeMs,
            nowMs = nowMs,
        ) ?: return

        prevCpu = snap.cpuStat
        prevCores = snap.coreStats
        prevDisk = snap.disk.raw
        prevNetRx = snap.net.rxBytes
        prevNetTx = snap.net.txBytes
        prevTimeMs = nowMs

        val cpuHist = append(state.cpuHistory, snap.cpuUsagePct.toFloat())
        val memHist = append(state.memHistory, snap.mem.ramPct)
        val npuHist = append(state.npuHistory, snap.npu.hmxPct.toFloat())
        val gpuHist = append(state.gpuHistory, snap.gpu.loadPct.toFloat())
        val diskReadHist = append(state.diskReadHistory, (snap.disk.readBps / 1048576.0).toFloat())
        val diskWriteHist = append(state.diskWriteHistory, (snap.disk.writeBps / 1048576.0).toFloat())
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
            diskReadHistory = diskReadHist,
            diskWriteHistory = diskWriteHist,
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
            DISKMODEL=""; DISKVENDOR=""
            for b in /sys/block/sd* /sys/block/mmcblk* /sys/block/nvme*; do
              [ -d "${'$'}b" ] || continue
              if [ -f "${'$'}b/device/model" ]; then
                DISKMODEL=${'$'}(cat "${'$'}b/device/model" 2>/dev/null | tr -d '\n')
                DISKVENDOR=${'$'}(cat "${'$'}b/device/vendor" 2>/dev/null | tr -d '\n')
                break
              fi
            done
            if [ -z "${'$'}DISKMODEL" ] && [ -f /proc/devinfo/ufs ]; then
              DISKMODEL=${'$'}(sed -n 's/^Device version:[[:space:]]*//p' /proc/devinfo/ufs 2>/dev/null | head -n 1 | sed 's/[[:space:]]*$//')
              DISKVENDOR=${'$'}(sed -n 's/^Device manufacture:[[:space:]]*//p' /proc/devinfo/ufs 2>/dev/null | head -n 1 | sed 's/[[:space:]]*$//')
            fi
            if [ -z "${'$'}DISKMODEL" ] && [ -f /proc/scsi/scsi ]; then
              DISKVENDOR=${'$'}(awk '/Vendor:/{print ${'$'}2; exit}' /proc/scsi/scsi 2>/dev/null)
              DISKMODEL=${'$'}(awk '/Model:/{print ${'$'}2; exit}' /proc/scsi/scsi 2>/dev/null)
            fi
            if [ -z "${'$'}DISKMODEL" ] && [ -f /proc/hwinfo ]; then
              DISKLINE=${'$'}(grep -i -E 'ufs|flash|emmc' /proc/hwinfo 2>/dev/null | head -n 1)
              [ -n "${'$'}DISKLINE" ] && DISKMODEL="${'$'}DISKLINE"
            fi
            if [ -z "${'$'}DISKMODEL" ]; then
              DISKLINE=${'$'}(dmesg 2>/dev/null | grep -i -E 'ufs.*(model|vendor)|ufs_device_info' | head -n 1)
              [ -n "${'$'}DISKLINE" ] && DISKMODEL="${'$'}DISKLINE"
            fi
            echo "DISKMODEL=${'$'}DISKMODEL"
            echo "DISKVENDOR=${'$'}DISKVENDOR"
            DDRINFO=${'$'}(cat /proc/devinfo/ddr 2>/dev/null)
            RAMTYPE=${'$'}(printf '%s\n' "${'$'}DDRINFO" | sed -n 's/^Device version:[[:space:]]*//p' | head -n 1 | sed 's/[[:space:]]*$//')
            RAMINFO=${'$'}(printf '%s\n' "${'$'}DDRINFO" | sed -n 's/^Device manufacture:[[:space:]]*//p' | head -n 1 | sed 's/[[:space:]]*$//')
            if [ -z "${'$'}RAMINFO" ] && [ -f /proc/hwinfo ]; then
              RAMINFO=${'$'}(grep -i -E 'ddr|dram' /proc/hwinfo 2>/dev/null | head -n 1)
            fi
            if [ -z "${'$'}RAMINFO" ]; then
              RAMINFO=${'$'}(dmesg 2>/dev/null | grep -i -E 'ddr[ _-](manufacturer|part|type)|\bdram\b' | head -n 1)
            fi
            [ -z "${'$'}RAMINFO" ] && RAMINFO=${'$'}(getprop ro.boot.ddr_manufacturer 2>/dev/null)
            echo "RAMTYPE=${'$'}RAMTYPE"
            echo "RAMINFO=${'$'}RAMINFO"
            echo "RAMVENDOR=${'$'}(getprop ro.boot.ddr_vendor 2>/dev/null)"
            echo "RAMMODEL=${'$'}(getprop ro.boot.ddr_part 2>/dev/null)"
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
            DATA=${'$'}(awk '${'$'}2=="/data"{print ${'$'}1}' /proc/mounts 2>/dev/null | sed 's#.*/##')
            echo "DATA=${'$'}DATA"
            if [ -n "${'$'}DATA" ]; then
              echo "DSK=${'$'}(awk -v d="${'$'}DATA" '${'$'}3==d{print ${'$'}4, ${'$'}6, ${'$'}8, ${'$'}10, ${'$'}12, ${'$'}13}' /proc/diskstats 2>/dev/null)"
              echo "FSTYPE=${'$'}(awk '${'$'}2=="/data"{print ${'$'}3}' /proc/mounts 2>/dev/null)"
              echo "STATF=${'$'}(stat -f -c '%b %f %S' /data 2>/dev/null)"
            fi
            echo "UFSWRITEMB=${'$'}(cat /proc/oplus_storage/io_metrics/forever/ufs_total_write_size_mb 2>/dev/null)"
            echo "UFSREADMB=${'$'}(cat /proc/oplus_storage/io_metrics/forever/ufs_total_read_size_mb 2>/dev/null)"
            UFSH=${'$'}(ls /sys/devices/platform/soc/*/health_descriptor/life_time_estimation_a /sys/bus/platform/drivers/ufshcd/*/health_descriptor/life_time_estimation_a 2>/dev/null | head -n 1)
            [ -z "${'$'}UFSH" ] && UFSH=${'$'}(ls /sys/devices/platform/soc/*/health/life_time_estimation_a 2>/dev/null | head -n 1)
            if [ -n "${'$'}UFSH" ]; then
              echo "UFSLIFE=${'$'}(cat "${'$'}UFSH" 2>/dev/null | head -n 1)"
              echo "UFSEOL=${'$'}(cat "${'$'}{UFSH%/*}/eol_info" 2>/dev/null | head -n 1)"
              [ -z "${'$'}(cat "${'$'}{UFSH%/*}/eol_info" 2>/dev/null)" ] && echo "UFSEOL=${'$'}(cat "${'$'}{UFSH%/*}/pre_eol_info" 2>/dev/null | head -n 1)"
            fi
            awk '{ if (${'$'}1 ~ /^(wlan0|rmnet[0-9]+|eth0|dummy0|ccmni[0-9]+|pdp[0-9]+):${'$'}/) { name=${'$'}1; sub(/:${'$'}/,"",name); print "NET_"name"=" ${'$'}2" "${'$'}10; } }' /proc/net/dev 2>/dev/null
            K=/sys/class/kgsl/kgsl-3d0
            GOK=0
            if [ -d "${'$'}K" ]; then
              [ -r "${'$'}K/gpu_busy_percentage" ] && { read -r g < "${'$'}K/gpu_busy_percentage"; echo "GPUBUSY=${'$'}g"; }
              [ -r "${'$'}K/gpuclk" ] && { read -r g < "${'$'}K/gpuclk"; echo "GPUCLK=${'$'}g"; }
              [ -r "${'$'}K/devfreq/max_freq" ] && { read -r g < "${'$'}K/devfreq/max_freq"; echo "GPUMAX=${'$'}g"; }
              [ -r "${'$'}K/devfreq/min_freq" ] && { read -r g < "${'$'}K/devfreq/min_freq"; echo "GPUMIN=${'$'}g"; }
              [ -r "${'$'}K/devfreq/governor" ] && { read -r g < "${'$'}K/devfreq/governor"; echo "GPUGOV=${'$'}g"; }
              [ -r "${'$'}K/temp" ] && { read -r g < "${'$'}K/temp"; echo "GPUTEMP=${'$'}g"; }
              [ -r "${'$'}K/gpu_model" ] && { read -r g < "${'$'}K/gpu_model"; echo "GPUMODEL=${'$'}g"; }
              GOK=1
            fi
            if [ "${'$'}GOK" = 0 ]; then
              # 联发科 Dimensity：devfreq/GED 可能冻结，优先 gpufreq_status
              if [ -r /proc/gpufreqv2/gpufreq_status ]; then
                GSTAT=${'$'}(cat /proc/gpufreqv2/gpufreq_status 2>/dev/null)
                GBUSY=${'$'}(printf '%s\n' "${'$'}GSTAT" | awk -F: '/[Uu]til|[Ll]oading/{gsub(/[^0-9.]/,"",${'$'}2); print ${'$'}2; exit}')
                [ -n "${'$'}GBUSY" ] && echo "GPUBUSY=${'$'}GBUSY"
                GFRQ=${'$'}(printf '%s\n' "${'$'}GSTAT" | awk -F: '/cur_freq|current_freq/{gsub(/[^0-9]/,"",${'$'}2); print ${'$'}2; exit}')
                [ -n "${'$'}GFRQ" ] && echo "GPUCLK=${'$'}GFRQ"
                GMAX=${'$'}(printf '%s\n' "${'$'}GSTAT" | awk -F: '/max_freq/{gsub(/[^0-9]/,"",${'$'}2); print ${'$'}2; exit}')
                [ -n "${'$'}GMAX" ] && echo "GPUMAX=${'$'}GMAX"
                GOK=1
              fi
            fi
            if [ "${'$'}GOK" = 0 ] && [ -d /sys/kernel/gpu ]; then
              [ -r /sys/kernel/gpu/gpu_busy ] && { read -r g < /sys/kernel/gpu/gpu_busy; echo "GPUBUSY=${'$'}g"; }
              [ -r /sys/kernel/gpu/gpu_clock ] && { read -r g < /sys/kernel/gpu/gpu_clock; echo "GPUCLK=${'$'}g"; }
              [ -r /sys/kernel/gpu/gpu_model ] && { read -r g < /sys/kernel/gpu/gpu_model; echo "GPUMODEL=${'$'}g"; }
              GOK=1
            fi
            if [ "${'$'}GOK" = 0 ] && [ -d /sys/devices/platform/gpusysfs ]; then
              [ -r /sys/devices/platform/gpusysfs/gpu_busy ] && { read -r g < /sys/devices/platform/gpusysfs/gpu_busy; echo "GPUBUSY=${'$'}g"; }
              [ -r /sys/devices/platform/gpusysfs/gpu_clock ] && { read -r g < /sys/devices/platform/gpusysfs/gpu_clock; echo "GPUCLK=${'$'}g"; }
              GOK=1
            fi
            if [ "${'$'}GOK" = 0 ]; then
              # 通用 Mali / Xclipse devfreq
              for d in /sys/class/devfreq/*mali* /sys/class/devfreq/*gpu*; do
                [ -d "${'$'}d" ] || continue
                [ -r "${'$'}d/cur_freq" ] && { read -r g < "${'$'}d/cur_freq"; echo "GPUCLK=${'$'}g"; }
                [ -r "${'$'}d/max_freq" ] && { read -r g < "${'$'}d/max_freq"; echo "GPUMAX=${'$'}g"; }
                [ -r "${'$'}d/min_freq" ] && { read -r g < "${'$'}d/min_freq"; echo "GPUMIN=${'$'}g"; }
                [ -r "${'$'}d/governor" ] && { read -r g < "${'$'}d/governor"; echo "GPUGOV=${'$'}g"; }
                GOK=1
                break
              done
            fi
            [ "${'$'}GOK" = 0 ] && [ -r /sys/module/pvrsrvkm/parameters/sgx_gpu_clk ] && { read -r g < /sys/module/pvrsrvkm/parameters/sgx_gpu_clk; echo "GPUCLK=${'$'}g"; }
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
            NPUOK=0
            if [ -n "${'$'}DBG" ] && [ -f "${'$'}STAT" ]; then
              awk '
                /HMX utilization/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_HMX="v }
                /HVX utilization/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_HVX="v }
                /Average Q6 load/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_Q6="v }
                /Current core clock/ { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_CLK="v }
                /HMX Power state/ { v=${'$'}0; sub(/.*:[ \t]*/,"",v); print "NPU_PWR="v }
                /^Count = / { v=${'$'}0; sub(/.*=[ \t]*/,"",v); print "NPU_LPM="v }
              ' "${'$'}STAT"
              NPUOK=1
            fi
            if [ "${'$'}NPUOK" != 1 ] && [ -r /sys/devices/virtual/npu/msm_npu/pwr ]; then
              echo "NPU_AVAIL=1"
              echo "NPU_PWR=${'$'}(cat /sys/devices/virtual/npu/msm_npu/pwr 2>/dev/null | head -n 1)"
            fi
        """.trimIndent()
    }
}
