package com.nzshores.llmserver.core.repository

import com.nzshores.llmserver.core.model.ModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for both remote (Hugging Face) model discovery and the local library of
 * downloaded models. Implementations own the download pipeline; callers only see state changes
 * through [library] and [search].
 */
interface ModelRepository {

    /** Remote search against the model hub. Empty query returns an empty list, not the whole hub. */
    suspend fun search(query: String, ggufOnly: Boolean = true): Result<List<ModelInfo>>

    /** All models known locally (any download state), newest state first. */
    fun library(): Flow<List<ModelInfo>>

    /** Enqueue (or resume) a download for the given quantization of a model. */
    suspend fun download(model: ModelInfo, wifiOnly: Boolean = true)

    suspend fun pauseDownload(modelId: String)

    suspend fun cancelDownload(modelId: String)

    suspend fun deleteLocal(modelId: String)
}
