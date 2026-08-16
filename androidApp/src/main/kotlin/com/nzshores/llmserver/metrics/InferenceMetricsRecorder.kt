package com.nzshores.llmserver.metrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val HISTORY_SIZE = 30

/**
 * Process-wide sink for the numbers only the inference/server layer can observe (tokens/s,
 * queue depth, VRAM, latency). Kept as a plain singleton rather than threaded through every
 * constructor because both [com.nzshores.llmserver.engine.llama.LlamaCppInferenceEngine] and
 * [com.nzshores.llmserver.server.KtorApiServer] need to write to it, while only
 * [AndroidMetricsCollector] reads it.
 */
object InferenceMetricsRecorder {

    private val _tokensPerSecond = MutableStateFlow(0f)
    val tokensPerSecond: StateFlow<Float> = _tokensPerSecond

    private val _lastLatencyMillis = MutableStateFlow(0L)
    val lastLatencyMillis: StateFlow<Long> = _lastLatencyMillis

    private val _queueDepth = MutableStateFlow(0)
    val queueDepth: StateFlow<Int> = _queueDepth

    private val _vramUsedBytes = MutableStateFlow(0L)
    val vramUsedBytes: StateFlow<Long> = _vramUsedBytes

    private val _tokensPerSecondHistory = MutableStateFlow(List(HISTORY_SIZE) { 0f })
    val tokensPerSecondHistory: StateFlow<List<Float>> = _tokensPerSecondHistory

    fun recordGeneration(tokenCount: Int, elapsedMillis: Long) {
        val tokensPerSecond = if (elapsedMillis > 0) tokenCount * 1000f / elapsedMillis else 0f
        _tokensPerSecond.value = tokensPerSecond
        _lastLatencyMillis.value = elapsedMillis
        _tokensPerSecondHistory.update { (it.drop(1) + tokensPerSecond) }
    }

    fun setQueueDepth(depth: Int) {
        _queueDepth.value = depth
    }

    fun setVramUsedBytes(bytes: Long) {
        _vramUsedBytes.value = bytes
    }

    fun reset() {
        _tokensPerSecond.value = 0f
        _lastLatencyMillis.value = 0L
        _queueDepth.value = 0
        _vramUsedBytes.value = 0L
    }
}
