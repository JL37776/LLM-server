package com.nzshores.llmserver.core.model

import kotlinx.serialization.Serializable

enum class ModelFormat { GGUF, ONNX, UNKNOWN }

enum class DownloadState { NOT_DOWNLOADED, QUEUED, DOWNLOADING, PAUSED, DOWNLOADED, FAILED }

@Serializable
data class QuantInfo(
    val label: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/**
 * A single model as known to the app: identity from Hugging Face plus whatever
 * local download/library state we have tracked for it. Search results and library
 * entries are both represented by this same shape so one repository can serve both screens.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val org: String,
    val format: ModelFormat,
    val parameterCount: String,
    val contextLength: Int?,
    val quantizations: List<QuantInfo>,
    val selectedQuant: QuantInfo?,
    val updatedAt: String?,
    val localPath: String?,
    val checksum: String?,
    val downloadState: DownloadState,
    val downloadProgressPercent: Int,
)
