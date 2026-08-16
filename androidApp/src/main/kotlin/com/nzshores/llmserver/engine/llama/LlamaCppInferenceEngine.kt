package com.nzshores.llmserver.engine.llama

import com.nzshores.llmserver.core.engine.InferenceEngine
import com.nzshores.llmserver.core.model.ActiveBackend
import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.EngineStatus
import com.nzshores.llmserver.core.model.GenParams
import com.nzshores.llmserver.core.model.LoadFailureReason
import com.nzshores.llmserver.core.model.LoadResult
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.core.model.Token
import com.nzshores.llmserver.engine.llama.jni.LlamaNative
import com.nzshores.llmserver.metrics.InferenceMetricsRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/** All GPU layers offloaded - llama.cpp clamps this to the model's actual layer count. */
private const val ALL_GPU_LAYERS = 999

class LlamaCppInferenceEngine : InferenceEngine {

    private val _status = MutableStateFlow(
        EngineStatus(
            loadedModelId = null,
            loadedModelName = null,
            backend = ActiveBackend.NONE,
            devicePreference = DevicePreference.GPU_FIRST,
            fellBackToCpu = false,
            loadedAtEpochMillis = null,
        ),
    )
    override val status: StateFlow<EngineStatus> = _status

    @Volatile private var handle: Long = 0

    override suspend fun load(model: ModelInfo, preference: DevicePreference): LoadResult = withContext(Dispatchers.IO) {
        unload()

        val path = model.localPath
        if (path == null || !File(path).exists()) {
            return@withContext LoadResult(
                success = false,
                backend = ActiveBackend.NONE,
                fellBackToCpu = false,
                failureReason = LoadFailureReason.FILE_NOT_FOUND,
                message = "Model file not found on device - re-download it.",
                vramUsedBytes = null,
            )
        }

        val wantsGpu = preference != DevicePreference.CPU_ONLY
        var result = tryLoad(path, useGpu = wantsGpu)

        var fellBack = false
        if (!result.success && wantsGpu && preference == DevicePreference.GPU_FIRST) {
            fellBack = true
            result = tryLoad(path, useGpu = false)
        }

        if (result.success) {
            _status.update {
                EngineStatus(
                    loadedModelId = model.id,
                    loadedModelName = model.name,
                    backend = result.backend,
                    devicePreference = preference,
                    fellBackToCpu = fellBack,
                    loadedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            InferenceMetricsRecorder.setVramUsedBytes(result.vramUsedBytes ?: 0L)
        }

        result.copy(fellBackToCpu = fellBack)
    }

    private fun tryLoad(path: String, useGpu: Boolean): LoadResult {
        val newHandle = LlamaNative.nativeLoadModel(path, useGpu, if (useGpu) ALL_GPU_LAYERS else 0)
        if (newHandle == 0L) {
            val error = LlamaNative.nativeGetLastError()
            return LoadResult(
                success = false,
                backend = ActiveBackend.NONE,
                fellBackToCpu = false,
                failureReason = classifyLoadFailure(error, useGpu),
                message = error,
                vramUsedBytes = null,
            )
        }
        handle = newHandle
        return LoadResult(
            success = true,
            backend = if (useGpu) ActiveBackend.GPU else ActiveBackend.CPU,
            fellBackToCpu = false,
            failureReason = LoadFailureReason.NONE,
            message = null,
            vramUsedBytes = if (useGpu) LlamaNative.nativeVramUsedBytes(newHandle) else null,
        )
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        if (handle != 0L) {
            LlamaNative.nativeFree(handle)
            handle = 0
        }
        _status.update {
            it.copy(loadedModelId = null, loadedModelName = null, backend = ActiveBackend.NONE, loadedAtEpochMillis = null)
        }
        InferenceMetricsRecorder.reset()
    }

    override fun generate(prompt: String, params: GenParams): Flow<Token> = callbackFlow {
        val activeHandle = handle
        if (activeHandle == 0L) {
            close()
            return@callbackFlow
        }

        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        withContext(Dispatchers.IO) {
            LlamaNative.nativeGenerate(
                activeHandle,
                prompt,
                params.maxTokens,
                params.temperature,
                params.topP,
                params.topK,
                params.repeatPenalty,
            ) { text, isFinal ->
                tokenCount++
                trySend(Token(text, isFinal))
                if (isFinal) {
                    InferenceMetricsRecorder.recordGeneration(tokenCount, System.currentTimeMillis() - startTime)
                    close()
                }
            }
        }
        awaitClose { }
    }
}
