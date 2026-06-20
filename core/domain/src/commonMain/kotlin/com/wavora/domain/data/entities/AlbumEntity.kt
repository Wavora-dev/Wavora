package com.wavora.domain.model.entities

import androidx.room.Entity
<<<<<<< HEAD
=======
import androidx.room.Index
>>>>>>> 56d2aea (fix)
import androidx.room.PrimaryKey
import com.wavora.domain.model.type.PlaylistType
import com.wavora.domain.model.type.RecentlyType
import com.wavora.domain.extension.now
import kotlinx.datetime.LocalDateTime

<<<<<<< HEAD
@Entity(tableName = "album")
=======
@Entity(
    tableName = "album",
    indices = [
        Index(value = ["liked"]),
        Index(value = ["downloadState"]),
    ],
)
>>>>>>> 56d2aea (fix)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = false) val browseId: String = "",
    val artistId: List<String?>? = null,
    val artistName: List<String>? = null,
    val audioPlaylistId: String,
    val description: String,
    val duration: String?,
    val durationSeconds: Int,
    val thumbnails: String?,
    val title: String,
    val trackCount: Int,
    val tracks: List<String>? = null,
    val type: String,
    val year: String?,
    val liked: Boolean = false,
    val inLibrary: LocalDateTime = now(),
    val favoriteAt: LocalDateTime? = now(),
    val downloadedAt: LocalDateTime? = now(),
    val downloadState: Int = DownloadState.STATE_NOT_DOWNLOADED,
) : PlaylistType,
    RecentlyType {
    override fun objectType(): RecentlyType.Type = RecentlyType.Type.ALBUM

    override fun playlistType(): PlaylistType.Type = PlaylistType.Type.ALBUM
}