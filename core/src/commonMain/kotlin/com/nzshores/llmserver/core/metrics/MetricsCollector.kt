package com.nzshores.llmserver.core.metrics

import com.nzshores.llmserver.core.model.Metrics
import kotlinx.coroutines.flow.StateFlow

interface MetricsCollector {

    val metrics: StateFlow<Metrics>

    fun start()

    fun stop()
}
