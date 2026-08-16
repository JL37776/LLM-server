package com.nzshores.llmserver.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.nzshores.llmserver.core.model.DownloadState
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM models ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun get(id: String): ModelEntity?

    @Upsert
    suspend fun upsert(entity: ModelEntity)

    @Query("UPDATE models SET downloadState = :state WHERE id = :id")
    suspend fun updateState(id: String, state: DownloadState)

    @Query("UPDATE models SET downloadProgressPercent = :percent, downloadState = :state WHERE id = :id")
    suspend fun updateProgress(id: String, percent: Int, state: DownloadState = DownloadState.DOWNLOADING)

    @Query("UPDATE models SET downloadState = :state, downloadProgressPercent = 100, localPath = :localPath WHERE id = :id")
    suspend fun markDownloaded(id: String, localPath: String, state: DownloadState = DownloadState.DOWNLOADED)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)
}
