package com.nzshores.llmserver.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.core.model.ModelFormat
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.core.model.QuantInfo

@Entity(tableName = "models")
@TypeConverters(Converters::class)
data class ModelEntity(
    @PrimaryKey val id: String,
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

fun ModelEntity.toDomain() = ModelInfo(
    id = id,
    name = name,
    org = org,
    format = format,
    parameterCount = parameterCount,
    contextLength = contextLength,
    quantizations = quantizations,
    selectedQuant = selectedQuant,
    updatedAt = updatedAt,
    localPath = localPath,
    checksum = checksum,
    downloadState = downloadState,
    downloadProgressPercent = downloadProgressPercent,
)

fun ModelInfo.toEntity() = ModelEntity(
    id = id,
    name = name,
    org = org,
    format = format,
    parameterCount = parameterCount,
    contextLength = contextLength,
    quantizations = quantizations,
    selectedQuant = selectedQuant,
    updatedAt = updatedAt,
    localPath = localPath,
    checksum = checksum,
    downloadState = downloadState,
    downloadProgressPercent = downloadProgressPercent,
)
