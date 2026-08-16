package com.nzshores.llmserver.metrics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.nzshores.llmserver.core.metrics.MetricsCollector
import com.nzshores.llmserver.core.model.Metrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.RandomAccessFile

private const val POLL_INTERVAL_MILLIS = 1000L

class AndroidMetricsCollector(
    private val context: Context,
) : MetricsCollector {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    private val _metrics = MutableStateFlow(emptyMetrics())
    override val metrics: StateFlow<Metrics> = _metrics

    private var lastCpuSample: CpuSample? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                _metrics.value = Metrics(
                    tokensPerSecond = InferenceMetricsRecorder.tokensPerSecond.value,
                    queueDepth = InferenceMetricsRecorder.queueDepth.value,
                    vramUsedBytes = InferenceMetricsRecorder.vramUsedBytes.value,
                    lastLatencyMillis = InferenceMetricsRecorder.lastLatencyMillis.value,
                    cpuUsagePercent = readCpuUsagePercent(),
                    ramUsagePercent = readRamUsagePercent(),
                    batteryTempCelsius = readBatteryTempCelsius(),
                    tokensPerSecondHistory = InferenceMetricsRecorder.tokensPerSecondHistory.value,
                )
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private fun readRamUsagePercent(): Float {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0f
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.totalMem <= 0) return 0f
        return ((info.totalMem - info.availMem).toFloat() / info.totalMem.toFloat()) * 100f
    }

    private fun readBatteryTempCelsius(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0f
        val tenthsOfDegree = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        return tenthsOfDegree / 10f
    }

    /**
     * Reads system-wide CPU usage from /proc/stat, comparing against the previous poll.
     * Some OEM builds sandbox /proc/stat for non-system apps; if the read fails, this quietly
     * reports 0 rather than crashing the monitor screen.
     */
    private fun readCpuUsagePercent(): Float {
        val sample = runCatching {
            RandomAccessFile("/proc/stat", "r").use { it.readLine() }
        }.getOrNull() ?: return 0f

        val parts = sample.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return 0f

        val idle = parts[3] + (parts.getOrElse(4) { 0L })
        val total = parts.sum()

        val previous = lastCpuSample
        lastCpuSample = CpuSample(idle, total)

        if (previous == null) return 0f
        val idleDelta = idle - previous.idle
        val totalDelta = total - previous.total
        if (totalDelta <= 0) return 0f

        return (1f - idleDelta.toFloat() / totalDelta.toFloat()) * 100f
    }

    private data class CpuSample(val idle: Long, val total: Long)

    companion object {
        fun emptyMetrics() = Metrics(
            tokensPerSecond = 0f,
            queueDepth = 0,
            vramUsedBytes = 0L,
            lastLatencyMillis = 0L,
            cpuUsagePercent = 0f,
            ramUsagePercent = 0f,
            batteryTempCelsius = 0f,
            tokensPerSecondHistory = List(30) { 0f },
        )
    }
}
