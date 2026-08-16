package com.nzshores.llmserver.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzshores.llmserver.core.model.ServerConfig
import com.nzshores.llmserver.core.model.ServerRuntimeInfo
import com.nzshores.llmserver.core.server.ApiServer
import com.nzshores.llmserver.data.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerViewModel(
    private val apiServer: ApiServer,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val runtimeInfo: StateFlow<ServerRuntimeInfo> = apiServer.runtimeInfo.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        apiServer.runtimeInfo.value,
    )

    init {
        viewModelScope.launch { apiServer.updateConfig(settingsRepository.loadServerConfig()) }
    }

    fun onToggleServer() {
        viewModelScope.launch {
            val running = runtimeInfo.value.isRunning
            if (running) apiServer.stop() else apiServer.start(runtimeInfo.value.config)
        }
    }

    fun onUpdateConfig(config: ServerConfig) {
        viewModelScope.launch {
            apiServer.updateConfig(config)
            settingsRepository.saveServerConfig(config)
        }
    }

    fun onPortChange(delta: Int) {
        val current = runtimeInfo.value.config
        val newPort = (current.port + delta).coerceIn(1024, 65535)
        onUpdateConfig(current.copy(port = newPort))
    }

    fun onMaxConcurrentChange(delta: Int) {
        val current = runtimeInfo.value.config
        val newMax = (current.maxConcurrentRequests + delta).coerceIn(1, 8)
        onUpdateConfig(current.copy(maxConcurrentRequests = newMax))
    }

    fun onToggleRequireApiKey() {
        val current = runtimeInfo.value.config
        onUpdateConfig(current.copy(requireApiKey = !current.requireApiKey))
    }

    fun onToggleSubnetRestriction() {
        val current = runtimeInfo.value.config
        onUpdateConfig(current.copy(restrictToLanSubnet = !current.restrictToLanSubnet))
    }
}
