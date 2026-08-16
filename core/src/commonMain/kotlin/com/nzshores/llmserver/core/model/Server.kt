package com.nzshores.llmserver.core.model

data class ServerConfig(
    val port: Int = 8080,
    val requireApiKey: Boolean = true,
    val apiKey: String? = null,
    val restrictToLanSubnet: Boolean = true,
    val maxConcurrentRequests: Int = 2,
)

data class ServerRuntimeInfo(
    val isRunning: Boolean,
    val lanIpAddress: String?,
    val config: ServerConfig,
)
