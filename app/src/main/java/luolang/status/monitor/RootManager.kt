package luolang.status.monitor

import java.util.concurrent.TimeUnit

/**
 * Root 权限检测与命令执行。
 *
 * 优先复用常驻的 [RootShell]（su 只提权一次），失败时回退到一次性的 `su -c`。
 */
object RootManager {

    /**
     * 以 root 身份执行 shell 命令，合并 stdout/stderr 返回完整输出。
     * 执行失败（无 su、拒绝授权等）返回 null。
     */
    fun execShell(command: String): String? {
        RootShell.exec(command)?.let { return it }
        return execOnce(command)
    }

    /** 一次性执行，作为常驻 shell 不可用时的兜底。 */
    private fun execOnce(command: String): String? = runCatching {
        val proc = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()

        val out = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor(5, TimeUnit.SECONDS)
        out
    }.getOrNull()

    /**
     * 执行 `id`，成功且为 root（uid=0）返回 true。
     */
    fun hasRoot(): Boolean {
        // 检测阶段用一次性 su -c，避免未授权时在常驻 shell 上等待握手超时
        val out = execOnce("id") ?: return false
        return out.contains("uid=0") && out.contains("root")
    }
}
