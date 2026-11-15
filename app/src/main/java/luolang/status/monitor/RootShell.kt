package luolang.status.monitor

import java.io.BufferedWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 常驻 root shell。
 *
 * 首次调用时 `su` 提权一次，之后所有命令都复用同一个 shell：
 *  - 不再每次采样都 spawn `su -c`，省掉 su 握手与 shell 启动开销；
 *  - 用唯一结束标记 + 超时读回输出，避免阻塞；
 *  - shell 异常退出时自动重建。
 */
object RootShell {

    private const val READY_PREFIX = "__WATCH_READY__"
    private const val END_PREFIX = "__WATCH_END__"

    private val seq = AtomicLong(0)
    private val queue = LinkedBlockingQueue<String>()
    private val lock = Any()

    private var process: Process? = null
    private var writer: BufferedWriter? = null

    /** 执行一条 shell 命令（非交互），返回合并后的 stdout/stderr。超时或失败返回 null。 */
    fun exec(command: String, timeoutMs: Long = 8000L): String? = synchronized(lock) {
        if (process?.isAlive != true || writer == null) {
            if (!start()) return null
        }

        val id = seq.incrementAndGet()
        val marker = "$END_PREFIX${id}__"
        val w = writer ?: return null

        val wrote = runCatching {
            w.write(command.trimEnd('\n'))
            w.write("\necho $marker\n")
            w.flush()
            true
        }.getOrDefault(false)
        if (!wrote) {
            close()
            return null
        }

        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remain = deadline - System.currentTimeMillis()
            if (remain <= 0) {
                close()
                return null
            }
            val line = queue.poll(remain, TimeUnit.MILLISECONDS) ?: run {
                close()
                return null
            }
            if (line.trim() == marker) return sb.toString()
            sb.append(line).append('\n')
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    /** 关闭常驻 shell（下次 exec 会重新 su）。 */
    fun close() {
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        writer = null
        process = null
        queue.clear()
    }

    private fun start(): Boolean {
        // 不同 root 管理器对交互式 su 的支持略有差异，依次尝试
        for (cmd in listOf(listOf("su"), listOf("su", "-s", "/system/bin/sh"))) {
            if (tryStart(cmd)) return true
        }
        return false
    }

    private fun tryStart(args: List<String>): Boolean {
        close()
        val p = runCatching {
            ProcessBuilder(args).redirectErrorStream(true).start()
        }.getOrNull() ?: return false

        val w = runCatching { p.outputStream.bufferedWriter() }.getOrNull()
        if (w == null) {
            runCatching { p.destroy() }
            return false
        }
        process = p
        writer = w

        Thread({
            runCatching {
                val reader = p.inputStream.bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    queue.put(line)
                }
            }
        }, "watch-root-reader").apply { isDaemon = true }.start()

        val ready = runCatching {
            w.write("export PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/data/local/tmp:/sbin:\$PATH\n")
            w.write("echo $READY_PREFIX${seq.incrementAndGet()}\n")
            w.flush()

            val deadline = System.currentTimeMillis() + 8000L
            while (true) {
                val remain = deadline - System.currentTimeMillis()
                if (remain <= 0) return@runCatching false
                val line = queue.poll(remain, TimeUnit.MILLISECONDS) ?: return@runCatching false
                if (line.contains(READY_PREFIX)) return@runCatching true
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }.getOrDefault(false)

        if (!ready) close()
        return ready
    }
}
