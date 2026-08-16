package com.nzshores.llmserver.core.model

enum class DevicePreference { GPU_FIRST, GPU_ONLY, CPU_ONLY }

enum class ActiveBackend { NONE, GPU, CPU }

enum class LoadFailureReason { NONE, OUT_OF_VRAM, DRIVER_UNSUPPORTED, UNSUPPORTED_OP, FILE_NOT_FOUND, UNKNOWN }

/**
 * Result of an InferenceEngine.load() call. `fellBackToCpu` plus `failureReason` lets the UI
 * show an honest "auto-fell-back" banner instead of silently switching backends.
 */
data class LoadResult(
    val success: Boolean,
    val backend: ActiveBackend,
    val fellBackToCpu: Boolean,
    val failureReason: LoadFailureReason,
    val message: String?,
    val vramUsedBytes: Long?,
)

data class EngineStatus(
    val loadedModelId: String?,
    val loadedModelName: String?,
    val backend: ActiveBackend,
    val devicePreference: DevicePreference,
    val fellBackToCpu: Boolean,
    val loadedAtEpochMillis: Long?,
    val hasVision: Boolean = false,
    val mmprojPath: String? = null,
)

data class GenParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val stop: List<String> = emptyList(),
)

data class Token(
    val text: String,
    val isFinal: Boolean,
)
