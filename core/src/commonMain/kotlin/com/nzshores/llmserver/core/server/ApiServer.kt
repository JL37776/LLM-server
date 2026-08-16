package com.nzshores.llmserver.core.server

import com.nzshores.llmserver.core.model.ServerConfig
import com.nzshores.llmserver.core.model.ServerRuntimeInfo
import kotlinx.coroutines.flow.StateFlow

/** LAN-facing OpenAI-compatible HTTP server, backed by whatever model an InferenceEngine has loaded. */
interface ApiServer {

    val runtimeInfo: StateFlow<ServerRuntimeInfo>

    suspend fun start(config: ServerConfig)

    suspend fun stop()

    suspend fun updateConfig(config: ServerConfig)
}
