package com.nzshores.llmserver.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Thin wrapper over the public Hugging Face Hub REST API (no auth token required for public
 * models). `?blobs=true` on the detail endpoint is what makes the Hub include per-file sizes.
 */
class HuggingFaceApi(private val client: HttpClient) {

    private val baseUrl = "https://huggingface.co"

    suspend fun search(query: String, ggufOnly: Boolean, limit: Int = 20): List<HfModelSummaryDto> {
        if (query.isBlank()) return emptyList()
        return client.get("$baseUrl/api/models") {
            parameter("search", query)
            parameter("limit", limit)
            if (ggufOnly) parameter("filter", "gguf")
        }.body()
    }

    suspend fun modelDetail(repoId: String): HfModelDetailDto {
        return client.get("$baseUrl/api/models/$repoId") {
            parameter("blobs", "true")
        }.body()
    }

    fun downloadUrl(repoId: String, fileName: String): String =
        "$baseUrl/$repoId/resolve/main/$fileName"
}
