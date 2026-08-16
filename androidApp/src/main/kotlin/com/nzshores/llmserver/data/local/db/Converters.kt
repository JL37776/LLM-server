package com.nzshores.llmserver.data.local.db

import androidx.room.TypeConverter
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.core.model.ModelFormat
import com.nzshores.llmserver.core.model.QuantInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromFormat(value: ModelFormat): String = value.name

    @TypeConverter
    fun toFormat(value: String): ModelFormat = runCatching { ModelFormat.valueOf(value) }.getOrDefault(ModelFormat.UNKNOWN)

    @TypeConverter
    fun fromDownloadState(value: DownloadState): String = value.name

    @TypeConverter
    fun toDownloadState(value: String): DownloadState = runCatching { DownloadState.valueOf(value) }.getOrDefault(DownloadState.NOT_DOWNLOADED)

    @TypeConverter
    fun fromQuantList(value: List<QuantInfo>): String = json.encodeToString(value)

    @TypeConverter
    fun toQuantList(value: String): List<QuantInfo> = runCatching { json.decodeFromString<List<QuantInfo>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromQuant(value: QuantInfo?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toQuant(value: String?): QuantInfo? = value?.let { runCatching { json.decodeFromString<QuantInfo>(it) }.getOrNull() }
}
