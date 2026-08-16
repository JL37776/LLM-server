package com.nzshores.llmserver.engine.llama.jni

/** Callback invoked from the native decode loop on whatever thread is running nativeGenerate. */
fun interface TokenCallback {
    fun onToken(text: String, isFinal: Boolean)
}

/**
 * Raw JNI surface over llama_bridge.cpp / llama.cpp. Kept intentionally thin - all retry,
 * fallback, and Flow-bridging logic lives in [com.nzshores.llmserver.engine.llama.LlamaCppInferenceEngine].
 */
object LlamaNative {

    init {
        System.loadLibrary("llama_bridge")
    }

    /** False when no GPU backend (Vulkan/OpenCL/etc.) was compiled into this build. */
    external fun nativeSupportsGpuOffload(): Boolean

    /** Returns 0 on failure; call [nativeGetLastError] to find out why. */
    external fun nativeLoadModel(modelPath: String, useGpu: Boolean, nGpuLayers: Int): Long

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

    external fun nativeVramUsedBytes(handle: Long): Long
}
