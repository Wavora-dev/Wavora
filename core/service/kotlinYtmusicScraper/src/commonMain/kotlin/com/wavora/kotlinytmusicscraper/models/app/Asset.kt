package com.wavora.scraper.models.wavora

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    @SerialName("browser_download_url")
    val browserDownloadUrl: String?,
    @SerialName("content_type")
    val contentType: String?,
    @SerialName("created_at")
    val createdAt: String?,
    @SerialName("download_count")
    val downloadCount: Int?,
    @SerialName("id")
    val id: Int?,
    @SerialName("label")
    val label: String?,
    @SerialName("name")
    val name: String?,
    @SerialName("node_id")
    val nodeId: String?,
    @SerialName("size")
    val size: Int?,
    @SerialName("state")
    val state: String?,
    @SerialName("updated_at")
    val updatedAt: String?,
    @SerialName("uploader")
    val uploader: Uploader?,
    @SerialName("url")
    val url: String?,
    // GitHub publishes this as "sha256:<hex>" for release assets (GA since
    // 2025) - used by WavoraUpdater to verify the downloaded zip before
    // installing it. Null for older releases uploaded before GitHub added
    // this field, or if GitHub simply hasn't computed it yet.
    @SerialName("digest")
    val digest: String? = null,
)