package com.nzshores.llmserver.core.model

data class Metrics(
    val tokensPerSecond: Float,
    val queueDepth: Int,
    val vramUsedBytes: Long,
    val lastLatencyMillis: Long,
    val cpuUsagePercent: Float,
    val ramUsagePercent: Float,
    val batteryTempCelsius: Float,
    val tokensPerSecondHistory: List<Float>,
)
