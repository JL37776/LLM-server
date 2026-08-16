package com.nzshores.llmserver.server

import android.content.Context
import android.content.Intent
import com.nzshores.llmserver.core.engine.InferenceEngine
import com.nzshores.llmserver.core.model.GenParams
import com.nzshores.llmserver.core.model.ServerConfig
import com.nzshores.llmserver.core.model.ServerRuntimeInfo
import com.nzshores.llmserver.core.server.ApiServer
import com.nzshores.llmserver.server.dto.ChatCompletionChoiceDto
import com.nzshores.llmserver.server.dto.ChatCompletionChunkChoiceDto
import com.nzshores.llmserver.server.dto.ChatCompletionChunkDeltaDto
import com.nzshores.llmserver.server.dto.ChatCompletionChunkDto
import com.nzshores.llmserver.server.dto.ChatCompletionRequestDto
import com.nzshores.llmserver.server.dto.ChatCompletionResponseDto
import com.nzshores.llmserver.server.dto.ChatMessageDto
import com.nzshores.llmserver.server.dto.ErrorDetailDto
import com.nzshores.llmserver.server.dto.ErrorResponseDto
import com.nzshores.llmserver.server.dto.ModelListEntryDto
import com.nzshores.llmserver.server.dto.ModelListResponseDto
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.plugins.origin
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import java.net.NetworkInterface
import java.util.UUID

class KtorApiServer(
    private val context: Context,
    private val engine: InferenceEngine,
) : ApiServer {

    private var server: ApplicationEngine? = null
    private var requestSemaphore = Semaphore(2)

    private val _runtimeInfo = MutableStateFlow(
        ServerRuntimeInfo(isRunning = false, lanIpAddress = lanIpAddress(), config = ServerConfig()),
    )
    override val runtimeInfo: StateFlow<ServerRuntimeInfo> = _runtimeInfo

    override suspend fun start(config: ServerConfig) {
        stopInternal()
        requestSemaphore = Semaphore(config.maxConcurrentRequests)

        server = embeddedServer(CIO, port = config.port) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(CallLogging)
            routing {
                get("/v1/models") {
                    val loaded = engine.status.value.loadedModelId
                    val entries = if (loaded != null) listOf(ModelListEntryDto(id = loaded)) else emptyList()
                    call.respond(ModelListResponseDto(data = entries))
                }
                post("/v1/chat/completions") {
                    val authHeader = call.request.header("Authorization")
                    val authorized = !config.requireApiKey || authHeader == "Bearer ${config.apiKey}"
                    if (!authorized) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto(ErrorDetailDto("Invalid or missing API key", "invalid_request_error")))
                        return@post
                    }

                    val remoteHost = call.request.origin.remoteHost
                    if (config.restrictToLanSubnet && !isPrivateAddress(remoteHost)) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponseDto(ErrorDetailDto("Client is outside the LAN subnet", "invalid_request_error")))
                        return@post
                    }

                    if (engine.status.value.loadedModelId == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponseDto(ErrorDetailDto("No model is loaded. Load a model first.", "model_not_loaded")))
                        return@post
                    }

                    if (!requestSemaphore.tryAcquire()) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponseDto(ErrorDetailDto("Server is at max concurrent requests", "rate_limit_error")))
                        return@post
                    }
                    com.nzshores.llmserver.metrics.InferenceMetricsRecorder.setQueueDepth(config.maxConcurrentRequests - requestSemaphore.availablePermits)
                    try {
                        val request = call.receive<ChatCompletionRequestDto>()
                        val prompt = request.messages.joinToString("\n") { "${it.role}: ${it.content}" }
                        val params = GenParams(
                            maxTokens = request.max_tokens ?: 512,
                            temperature = request.temperature ?: 0.7f,
                            topP = request.top_p ?: 0.9f,
                        )
                        val modelId = engine.status.value.loadedModelId ?: "unknown"
                        val id = "chatcmpl-${UUID.randomUUID()}"
                        val created = System.currentTimeMillis() / 1000

                        if (request.stream) {
                            call.respondTextWriter(ContentType.Text.EventStream) {
                                var first = true
                                engine.generate(prompt, params).fold(Unit) { _, token ->
                                    val chunk = ChatCompletionChunkDto(
                                        id = id,
                                        created = created,
                                        model = modelId,
                                        choices = listOf(
                                            ChatCompletionChunkChoiceDto(
                                                index = 0,
                                                delta = if (first) ChatCompletionChunkDeltaDto(role = "assistant", content = token.text) else ChatCompletionChunkDeltaDto(content = token.text),
                                                finish_reason = if (token.isFinal) "stop" else null,
                                            ),
                                        ),
                                    )
                                    first = false
                                    write("data: ${Json.encodeToString(ChatCompletionChunkDto.serializer(), chunk)}\n\n")
                                    flush()
                                }
                                write("data: [DONE]\n\n")
                                flush()
                            }
                        } else {
                            val builder = StringBuilder()
                            engine.generate(prompt, params).collect { token -> builder.append(token.text) }
                            call.respond(
                                ChatCompletionResponseDto(
                                    id = id,
                                    created = created,
                                    model = modelId,
                                    choices = listOf(
                                        ChatCompletionChoiceDto(index = 0, message = ChatMessageDto("assistant", builder.toString()), finish_reason = "stop"),
                                    ),
                                ),
                            )
                        }
                    } finally {
                        requestSemaphore.release()
                        com.nzshores.llmserver.metrics.InferenceMetricsRecorder.setQueueDepth(config.maxConcurrentRequests - requestSemaphore.availablePermits)
                    }
                }
            }
        }.start(wait = false)

        context.startForegroundService(Intent(context, ApiForegroundService::class.java))

        _runtimeInfo.update { ServerRuntimeInfo(isRunning = true, lanIpAddress = lanIpAddress(), config = config) }
    }

    override suspend fun stop() {
        stopInternal()
        _runtimeInfo.update { it.copy(isRunning = false) }
    }

    override suspend fun updateConfig(config: ServerConfig) {
        if (_runtimeInfo.value.isRunning) {
            start(config)
        } else {
            _runtimeInfo.update { it.copy(config = config) }
        }
    }

    private fun stopInternal() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        server = null
        context.stopService(Intent(context, ApiForegroundService::class.java))
    }

    private fun isPrivateAddress(host: String): Boolean {
        if (host == "127.0.0.1" || host == "localhost") return true
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            else -> false
        }
    }

    private fun lanIpAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { addr -> !addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false }
                ?.hostAddress
        }.getOrNull()
    }
}
