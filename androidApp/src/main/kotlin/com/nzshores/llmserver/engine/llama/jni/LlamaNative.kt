package com.nzshores.llmserver.engine.llama.jni

fun interface TokenCallback {
    fun onToken(text: String, isFinal: Boolean)
}

object LlamaNative {

    init {
        System.loadLibrary("llama_bridge")
    }

    external fun nativeSupportsGpuOffload(): Boolean

    external fun nativeLoadModel(modelPath: String, useGpu: Boolean, nGpuLayers: Int): Long

    external fun nativeLoadMmproj(handle: Long, mmprojPath: String): Boolean

    external fun nativeFreeMmproj(handle: Long)

    external fun nativeHasVision(handle: Long): Boolean

    external fun nativeGetLastError(): String

    external fun nativeFree(handle: Long)

    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenCallback,
    )

    external fun nativeGenerateWithImage(
        handle: Long,
        prompt: String,
        imageData: ByteArray,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenCallback,
    )

    external fun nativeVramUsedBytes(handle: Long): Long
}
