package com.nzshores.llmserver.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class HfModelSummaryDto(
    val id: String,
    val author: String? = null,
    val lastModified: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class HfModelDetailDto(
    val id: String,
    val siblings: List<HfSiblingDto> = emptyList(),
)

@Serializable
data class HfSiblingDto(
    val rfilename: String,
    val size: Long? = null,
)
