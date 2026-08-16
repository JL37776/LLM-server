package com.nzshores.llmserver.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ChatCompletionRequestDto(
    val model: String? = null,
    val messages: List<ChatMessageDto> = emptyList(),
    val stream: Boolean = false,
    val max_tokens: Int? = null,
    val temperature: Float? = null,
    val top_p: Float? = null,
)

@Serializable
data class ChatCompletionChoiceDto(
    val index: Int,
    val message: ChatMessageDto,
    val finish_reason: String,
)

@Serializable
data class ChatCompletionResponseDto(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatCompletionChoiceDto>,
)

@Serializable
data class ChatCompletionChunkDeltaDto(val role: String? = null, val content: String? = null)

@Serializable
data class ChatCompletionChunkChoiceDto(
    val index: Int,
    val delta: ChatCompletionChunkDeltaDto,
    val finish_reason: String? = null,
)

@Serializable
data class ChatCompletionChunkDto(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChatCompletionChunkChoiceDto>,
)

@Serializable
data class ModelListEntryDto(val id: String, val `object`: String = "model", val owned_by: String = "local")

@Serializable
data class ModelListResponseDto(val `object`: String = "list", val data: List<ModelListEntryDto>)

@Serializable
data class ErrorResponseDto(val error: ErrorDetailDto)

@Serializable
data class ErrorDetailDto(val message: String, val type: String)
