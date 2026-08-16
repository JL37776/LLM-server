package com.nzshores.llmserver.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzshores.llmserver.core.engine.InferenceEngine
import com.nzshores.llmserver.core.metrics.MetricsCollector
import com.nzshores.llmserver.core.model.EngineStatus
import com.nzshores.llmserver.core.model.Metrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MonitorUiState(
    val metrics: Metrics,
    val engineStatus: EngineStatus,
)

class MonitorViewModel(
    private val metricsCollector: MetricsCollector,
    private val engine: InferenceEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState(metricsCollector.metrics.value, engine.status.value))
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    init {
        metricsCollector.start()
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(metricsCollector.metrics, engine.status) { metrics, status ->
                MonitorUiState(metrics, status)
            }.collect { _uiState.value = it }
        }
    }

    override fun onCleared() {
        metricsCollector.stop()
        super.onCleared()
    }
}
