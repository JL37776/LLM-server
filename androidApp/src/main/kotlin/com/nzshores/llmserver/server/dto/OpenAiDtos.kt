package com.nzshores.llmserver.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: JsonElement,
) {
    fun textContent(): String {
        return when (content) {
            is JsonPrimitive -> content.jsonPrimitive.content
            is JsonArray -> content.jsonArray
                .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                .joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
            else -> content.toString()
        }
    }

    fun imageBase64(): String? {
        if (content !is JsonArray) return null
        for (part in content.jsonArray) {
            val obj = part.jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "image_url") continue
            val url = obj["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: continue
            val prefix = "data:image/"
            if (url.startsWith(prefix)) {
                val base64Start = url.indexOf(";base64,")
                if (base64Start >= 0) return url.substring(base64Start + 8)
            }
        }
        return null
    }
}

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
    val message: ResponseMessageDto,
    val finish_reason: String,
)

@Serializable
data class ResponseMessageDto(val role: String, val content: String)

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
