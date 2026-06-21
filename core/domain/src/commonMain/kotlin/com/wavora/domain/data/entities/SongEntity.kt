package com.wavora.domain.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wavora.domain.model.entities.DownloadState.STATE_NOT_DOWNLOADED
import com.wavora.domain.model.type.RecentlyType
import com.wavora.domain.extension.now
import kotlinx.datetime.LocalDateTime

// Indices added for: "liked songs" filter, "downloaded songs" filter, and the
// totalPlayTime/inLibrary sort orders used by Most Played / Library screens.
// Without these, every one of those queries does a full table scan over `song`.
@Entity(
    tableName = "song",
    indices = [
        Index(value = ["liked"]),
        Index(value = ["downloadState"]),
        Index(value = ["totalPlayTime"]),
        Index(value = ["inLibrary"]),
    ],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = false) val videoId: String = "",
    val albumId: String? = null,
    val albumName: String? = null,
    val artistId: List<String>? = null,
    val artistName: List<String>? = null,
    val duration: String,
    val durationSeconds: Int,
    val isAvailable: Boolean,
    val isExplicit: Boolean,
    val likeStatus: String,
    val thumbnails: String? = null,
    val title: String,
    val videoType: String,
    val category: String?,
    val resultType: String?,
    val liked: Boolean = false,
    val totalPlayTime: Long = 0,
    val downloadState: Int = STATE_NOT_DOWNLOADED,
    val favoriteAt: LocalDateTime? = now(),
    val downloadedAt: LocalDateTime? = now(),
    val inLibrary: LocalDateTime = now(),
    val canvasUrl: String? = null,
    val canvasThumbUrl: String? = null,
) : RecentlyType {
    override fun objectType(): RecentlyType.Type = RecentlyType.Type.SONG
}