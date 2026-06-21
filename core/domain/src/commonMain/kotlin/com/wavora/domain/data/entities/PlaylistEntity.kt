package com.wavora.domain.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wavora.domain.model.type.PlaylistType
import com.wavora.domain.model.type.RecentlyType
import com.wavora.domain.extension.now
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "playlist",
    indices = [
        Index(value = ["liked"]),
        Index(value = ["downloadState"]),
    ],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = false)
    val id: String = "",
    val author: String? = "",
    val description: String = "",
    val duration: String = "",
    val durationSeconds: Int = 0,
    val privacy: String = "PRIVATE",
    val thumbnails: String = "",
    val title: String,
    val trackCount: Int = 0,
    val tracks: List<String>? = null,
    val year: String? = null,
    val liked: Boolean = false,
    val inLibrary: LocalDateTime = now(),
    val favoriteAt: LocalDateTime? = now(),
    val downloadedAt: LocalDateTime? = now(),
    val downloadState: Int = DownloadState.STATE_NOT_DOWNLOADED,
) : PlaylistType,
    RecentlyType {
    override fun playlistType(): PlaylistType.Type =
        if (id.startsWith("RDEM") || id.startsWith("RDAMVM") || id.startsWith("RDAT")) {
            PlaylistType.Type.RADIO
        } else {
            PlaylistType.Type.YOUTUBE_PLAYLIST
        }

    override fun objectType(): RecentlyType.Type = RecentlyType.Type.PLAYLIST
}