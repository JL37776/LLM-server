package com.nzshores.llmserver.core.engine

import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.EngineStatus
import com.nzshores.llmserver.core.model.GenParams
import com.nzshores.llmserver.core.model.LoadResult
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.core.model.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns exactly one loaded model at a time. [load] must attempt the requested [DevicePreference]
 * first and only silently retry on CPU when the preference is GPU_FIRST - GPU_ONLY failures are
 * reported, never downgraded, so the UI can show the true reason rather than a bare "failed".
 */
interface InferenceEngine {

    val status: StateFlow<EngineStatus>

    suspend fun load(model: ModelInfo, preference: DevicePreference): LoadResult

    suspend fun unload()

    fun generate(prompt: String, params: GenParams = GenParams()): Flow<Token>
}
