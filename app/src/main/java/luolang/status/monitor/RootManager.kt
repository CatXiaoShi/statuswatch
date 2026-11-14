package luolang.status.monitor

import java.util.concurrent.TimeUnit

/**
 * 通过 `su -c` 执行底层命令，并做 root 检测。
 */
object RootManager {

    /**
     * 以 root 身份执行 shell 命令，合并 stdout/stderr 返回完整输出。
     * 执行失败（无 su、拒绝授权等）返回 null。
     */
    fun execShell(command: String): String? = runCatching {
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
        val out = execShell("id") ?: return false
        return out.contains("uid=0") && out.contains("root")
    }
}
