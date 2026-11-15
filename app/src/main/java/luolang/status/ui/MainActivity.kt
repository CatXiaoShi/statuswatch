package luolang.status.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import luolang.status.data.PowerSettings
import luolang.status.monitor.RootManager
import luolang.status.monitor.SystemMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RootState { Checking, Denied, Granted }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("watch_settings", MODE_PRIVATE)

        setContent {
            WatchTheme {
                var rootState by remember { mutableStateOf(RootState.Checking) }
                val monitor = remember { SystemMonitor() }
                val scope = rememberCoroutineScope()

                // 采样间隔（0.1s ~ 5s），持久化
                var intervalMs by remember {
                    mutableStateOf(prefs.getInt(KEY_INTERVAL, 1000))
                }

                // 功耗校准设置，持久化
                var powerSettings by remember {
                    mutableStateOf(
                        PowerSettings(
                            unit = prefs.getString(KEY_POWER_UNIT, "auto") ?: "auto",
                            invert = prefs.getBoolean(KEY_POWER_INVERT, false),
                            scale = prefs.getFloat(KEY_POWER_SCALE, 1.0f),
                        ),
                    )
                }
                androidx.compose.runtime.SideEffect {
                    monitor.powerSettings = powerSettings
                }

                // 首次启动做 root 检测
                LaunchedEffect(Unit) {
                    rootState = if (RootManager.hasRoot()) RootState.Granted else RootState.Denied
                }

                fun requestRoot() {
                    scope.launch {
                        rootState = RootState.Checking
                        rootState = if (RootManager.hasRoot()) RootState.Granted else RootState.Denied
                    }
                }

                when (rootState) {
                    RootState.Checking -> SplashScreen()

                    RootState.Denied -> RootBlockScreen(
                        onRequestAuth = ::requestRoot,
                        onExit = {
                            finish()
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                    )

                    RootState.Granted -> {
                        LaunchedEffect(Unit) {
                            monitor.applyDevice(monitor.loadDevice())
                        }
                        // 间隔变化时重启采集循环；扣掉采集自身耗时，使周期贴近设定值
                        LaunchedEffect(intervalMs) {
                            while (true) {
                                val start = SystemClock.elapsedRealtime()
                                monitor.collect()
                                val spent = SystemClock.elapsedRealtime() - start
                                delay((intervalMs - spent).coerceAtLeast(0L))
                            }
                        }
                        DashboardScreen(
                            state = monitor.state,
                            intervalMs = intervalMs,
                            onIntervalChange = { ms ->
                                intervalMs = ms
                                prefs.edit().putInt(KEY_INTERVAL, ms).apply()
                            },
                            powerSettings = powerSettings,
                            onPowerSettingsChange = { ps ->
                                powerSettings = ps
                                prefs.edit()
                                    .putString(KEY_POWER_UNIT, ps.unit)
                                    .putBoolean(KEY_POWER_INVERT, ps.invert)
                                    .putFloat(KEY_POWER_SCALE, ps.scale)
                                    .apply()
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_INTERVAL = "sample_interval_ms"
        const val KEY_POWER_UNIT = "power_unit"
        const val KEY_POWER_INVERT = "power_invert"
        const val KEY_POWER_SCALE = "power_scale"
    }
}
