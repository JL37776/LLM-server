package com.nzshores.llmserver.engine.llama

import android.util.Log
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

private const val TAG = "LlamaCppEngine"
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
        Log.i(TAG, "load() called: model.id=${model.id}, model.name=${model.name}, preference=$preference")
        Log.i(TAG, "  localPath=${model.localPath}")
        unload()

        val path = model.localPath
        if (path == null || !File(path).exists()) {
            Log.e(TAG, "  FILE NOT FOUND: path=$path, exists=${path?.let { File(it).exists() }}")
            return@withContext LoadResult(
                success = false,
                backend = ActiveBackend.NONE,
                fellBackToCpu = false,
                failureReason = LoadFailureReason.FILE_NOT_FOUND,
                message = "Model file not found on device, re-download it.",
                vramUsedBytes = null,
            )
        }

        val fileSize = File(path).length()
        Log.i(TAG, "  file exists, size=$fileSize bytes (${fileSize / 1024 / 1024} MB)")

        val wantsGpu = preference != DevicePreference.CPU_ONLY
        Log.i(TAG, "  wantsGpu=$wantsGpu, calling tryLoad...")
        val t0 = System.currentTimeMillis()
        var result = tryLoad(path, useGpu = wantsGpu)
        Log.i(TAG, "  tryLoad(gpu=$wantsGpu) took ${System.currentTimeMillis() - t0}ms, success=${result.success}")

        var fellBack = false
        if (!result.success && wantsGpu && preference == DevicePreference.GPU_FIRST) {
            Log.i(TAG, "  GPU failed, falling back to CPU...")
            fellBack = true
            val t1 = System.currentTimeMillis()
            result = tryLoad(path, useGpu = false)
            Log.i(TAG, "  tryLoad(gpu=false) took ${System.currentTimeMillis() - t1}ms, success=${result.success}")
        }

        if (result.success) {
            Log.i(TAG, "  load SUCCESS: backend=${result.backend}, fellBack=$fellBack, handle=$handle")
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
        } else {
            Log.e(TAG, "  load FAILED: reason=${result.failureReason}, msg=${result.message}")
        }

        result.copy(fellBackToCpu = fellBack)
    }

    private fun tryLoad(path: String, useGpu: Boolean): LoadResult {
        val supportsGpu = LlamaNative.nativeSupportsGpuOffload()
        if (useGpu && !supportsGpu) {
            return LoadResult(
                success = false,
                backend = ActiveBackend.NONE,
                fellBackToCpu = false,
                failureReason = LoadFailureReason.DRIVER_UNSUPPORTED,
                message = "This build has no GPU backend compiled in.",
                vramUsedBytes = null,
            )
        }

        val nGpuLayers = if (useGpu) ALL_GPU_LAYERS else 0
        val newHandle = LlamaNative.nativeLoadModel(path, useGpu, nGpuLayers)
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
        val vram = if (useGpu) LlamaNative.nativeVramUsedBytes(newHandle) else null
        return LoadResult(
            success = true,
            backend = if (useGpu) ActiveBackend.GPU else ActiveBackend.CPU,
            fellBackToCpu = false,
            failureReason = LoadFailureReason.NONE,
            message = null,
            vramUsedBytes = vram,
        )
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "unload() called, current handle=$handle")
            if (handle != 0L) {
                LlamaNative.nativeFree(handle)
                handle = 0
            }
            _status.update {
                it.copy(
                    loadedModelId = null,
                    loadedModelName = null,
                    backend = ActiveBackend.NONE,
                    loadedAtEpochMillis = null,
                    hasVision = false,
                    mmprojPath = null,
                )
            }
            InferenceMetricsRecorder.reset()
        }
    }

    override suspend fun loadMmproj(mmprojPath: String): Boolean = withContext(Dispatchers.IO) {
        val h = handle
        if (h == 0L) {
            Log.e(TAG, "loadMmproj: no text model loaded")
            return@withContext false
        }
        Log.i(TAG, "loadMmproj: $mmprojPath")
        val ok = LlamaNative.nativeLoadMmproj(h, mmprojPath)
        if (ok) {
            _status.update { it.copy(hasVision = LlamaNative.nativeHasVision(h), mmprojPath = mmprojPath) }
            Log.i(TAG, "loadMmproj: success, hasVision=${_status.value.hasVision}")
        } else {
            Log.e(TAG, "loadMmproj: failed, error=${LlamaNative.nativeGetLastError()}")
        }
        ok
    }

    override suspend fun unloadMmproj() {
        withContext(Dispatchers.IO) {
            val h = handle
            if (h != 0L) {
                LlamaNative.nativeFreeMmproj(h)
            }
            _status.update { it.copy(hasVision = false, mmprojPath = null) }
        }
    }

    override fun hasVision(): Boolean {
        val h = handle
        return h != 0L && LlamaNative.nativeHasVision(h)
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

    override fun generateWithImage(prompt: String, imageData: ByteArray, params: GenParams): Flow<Token> = callbackFlow {
        val activeHandle = handle
        if (activeHandle == 0L) {
            close()
            return@callbackFlow
        }

        Log.i(TAG, "generateWithImage: prompt_len=${prompt.length}, image_bytes=${imageData.size}")
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        withContext(Dispatchers.IO) {
            LlamaNative.nativeGenerateWithImage(
                activeHandle,
                prompt,
                imageData,
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
