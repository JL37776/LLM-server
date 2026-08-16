package com.nzshores.llmserver.core.engine

import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.EngineStatus
import com.nzshores.llmserver.core.model.GenParams
import com.nzshores.llmserver.core.model.LoadResult
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.core.model.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InferenceEngine {

    val status: StateFlow<EngineStatus>

    suspend fun load(model: ModelInfo, preference: DevicePreference): LoadResult

    suspend fun unload()

    suspend fun loadMmproj(mmprojPath: String): Boolean

    suspend fun unloadMmproj()

    fun hasVision(): Boolean

    fun generate(prompt: String, params: GenParams = GenParams()): Flow<Token>

    fun generateWithImage(prompt: String, imageData: ByteArray, params: GenParams = GenParams()): Flow<Token>
}
