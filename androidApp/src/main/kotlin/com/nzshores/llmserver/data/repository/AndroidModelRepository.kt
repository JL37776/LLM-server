package com.nzshores.llmserver.data.repository

import android.content.Context
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.core.model.ModelFormat
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.core.model.QuantInfo
import com.nzshores.llmserver.core.repository.ModelRepository
import com.nzshores.llmserver.data.download.DownloadManager
import com.nzshores.llmserver.data.local.db.ModelDao
import com.nzshores.llmserver.data.local.db.toDomain
import com.nzshores.llmserver.data.local.db.toEntity
import com.nzshores.llmserver.data.remote.HfModelSummaryDto
import com.nzshores.llmserver.data.remote.HuggingFaceApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val QUANT_LABEL_REGEX = Regex("(Q\\d[A-Z0-9_]*|F16|F32|BF16)", RegexOption.IGNORE_CASE)
private val PARAM_COUNT_REGEX = Regex("(\\d+(?:\\.\\d+)?)[Bb](?![a-zA-Z])")

class AndroidModelRepository(
    private val huggingFaceApi: HuggingFaceApi,
    private val modelDao: ModelDao,
    private val downloadManager: DownloadManager,
    private val context: Context,
) : ModelRepository {

    override suspend fun search(query: String, ggufOnly: Boolean): Result<List<ModelInfo>> = runCatching {
        huggingFaceApi.search(query, ggufOnly).map { summary -> summary.toModelInfo() }
    }

    override fun library(): Flow<List<ModelInfo>> = modelDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun download(model: ModelInfo, wifiOnly: Boolean) {
        modelDao.upsert(model.copy(downloadState = DownloadState.QUEUED, downloadProgressPercent = 0).toEntity())
        downloadManager.enqueue(model, wifiOnly)
    }

    override suspend fun pauseDownload(modelId: String) {
        downloadManager.cancel(modelId)
        modelDao.updateState(modelId, DownloadState.PAUSED)
    }

    override suspend fun cancelDownload(modelId: String) {
        downloadManager.cancel(modelId)
        modelDao.delete(modelId)
    }

    override suspend fun deleteLocal(modelId: String) {
        downloadManager.cancel(modelId)
        val entity = modelDao.get(modelId)
        entity?.localPath?.let { path -> File(path).delete() }
        File(context.filesDir, "models/$modelId").deleteRecursively()
        modelDao.delete(modelId)
    }

    private suspend fun HfModelSummaryDto.toModelInfo(): ModelInfo {
        val quantizations = runCatching { huggingFaceApi.modelDetail(id) }
            .getOrNull()
            ?.siblings
            ?.filter { it.rfilename.endsWith(".gguf", ignoreCase = true) }
            ?.map { sibling ->
                QuantInfo(
                    label = QUANT_LABEL_REGEX.find(sibling.rfilename)?.value?.uppercase() ?: "GGUF",
                    fileName = sibling.rfilename,
                    downloadUrl = huggingFaceApi.downloadUrl(id, sibling.rfilename),
                    sizeBytes = sibling.size ?: 0L,
                )
            }
            ?: emptyList()

        val preferredQuant = quantizations.firstOrNull { it.label.contains("Q4_K_M", ignoreCase = true) }
            ?: quantizations.firstOrNull()

        return ModelInfo(
            id = id,
            name = id.substringAfterLast('/'),
            org = author ?: id.substringBefore('/', missingDelimiterValue = id),
            format = ModelFormat.GGUF,
            parameterCount = PARAM_COUNT_REGEX.find(id)?.value?.uppercase() ?: "—",
            contextLength = null,
            quantizations = quantizations,
            selectedQuant = preferredQuant,
            updatedAt = lastModified?.take(10),
            localPath = null,
            checksum = null,
            downloadState = DownloadState.NOT_DOWNLOADED,
            downloadProgressPercent = 0,
        )
    }
}
