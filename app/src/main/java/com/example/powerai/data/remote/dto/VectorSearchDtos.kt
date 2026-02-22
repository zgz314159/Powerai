package com.example.powerai.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VectorSearchRequest(
    @SerializedName("query_text") val queryText: String,
    @SerializedName("limit") val limit: Int = 5,
    @SerializedName("collection") val collection: String? = null
)

data class VectorSearchHit(
    @SerializedName("unitName") val unitName: String? = null,
    @SerializedName("jobTitle") val jobTitle: String? = null,
    @SerializedName("source_file") val sourceFile: String? = null,
    @SerializedName("contentMarkdown") val contentMarkdown: String? = null,
    @SerializedName("distance") val distance: Double? = null
)

data class VectorSearchResponse(
    @SerializedName("results") val results: List<VectorSearchHit> = emptyList()
)
