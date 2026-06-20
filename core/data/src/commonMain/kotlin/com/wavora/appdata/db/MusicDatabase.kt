package com.wavora.appdata.db

import DatabaseDao
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wavora.domain.model.entities.AlbumEntity
import com.wavora.domain.model.entities.ArtistEntity
import com.wavora.domain.model.entities.EpisodeEntity
import com.wavora.domain.model.entities.FollowedArtistSingleAndAlbum
import com.wavora.domain.model.entities.GoogleAccountEntity
import com.wavora.domain.model.entities.LocalPlaylistEntity
import com.wavora.domain.model.entities.LyricsEntity
import com.wavora.domain.model.entities.NewFormatEntity
import com.wavora.domain.model.entities.NotificationEntity
import com.wavora.domain.model.entities.PairSongLocalPlaylist
import com.wavora.domain.model.entities.PlaylistEntity
import com.wavora.domain.model.entities.PodcastsEntity
import com.wavora.domain.model.entities.QueueEntity
import com.wavora.domain.model.entities.SearchHistory
import com.wavora.domain.model.entities.SetVideoIdEntity
import com.wavora.domain.model.entities.SongEntity
import com.wavora.domain.model.entities.SongInfoEntity
import com.wavora.domain.model.entities.TranslatedLyricsEntity
import com.wavora.domain.model.entities.YourYouTubePlaylistList
import com.wavora.domain.model.entities.analytics.EventArtistEntity
import com.wavora.domain.model.entities.analytics.PlaybackEventEntity

@Database(
    entities = [
        NewFormatEntity::class, SongInfoEntity::class, SearchHistory::class, SongEntity::class, ArtistEntity::class,
        AlbumEntity::class, PlaylistEntity::class, LocalPlaylistEntity::class, LyricsEntity::class, QueueEntity::class,
        SetVideoIdEntity::class, PairSongLocalPlaylist::class, GoogleAccountEntity::class, FollowedArtistSingleAndAlbum::class,
        NotificationEntity::class, TranslatedLyricsEntity::class, PodcastsEntity::class, EpisodeEntity::class,
        YourYouTubePlaylistList::class, PlaybackEventEntity::class, EventArtistEntity::class
    ],
<<<<<<< HEAD
    version = 22,
=======
    version = 23,
>>>>>>> 56d2aea (fix)
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3), AutoMigration(
            from = 1,
            to = 3,
        ), AutoMigration(from = 3, to = 4), AutoMigration(from = 2, to = 4), AutoMigration(
            from = 3,
            to = 5,
        ), AutoMigration(4, 5), AutoMigration(6, 7), AutoMigration(
            7,
            8,
            spec = AutoMigration7_8::class,
        ), AutoMigration(8, 9),
        AutoMigration(9, 10),
        AutoMigration(from = 11, to = 12, spec = AutoMigration11_12::class),
        AutoMigration(13, 14),
        AutoMigration(14, 15),
        AutoMigration(15, 16),
        AutoMigration(16, 17),
        AutoMigration(17, 18),
        AutoMigration(16, 18),
        AutoMigration(15, 18),
        AutoMigration(18, 19),
        AutoMigration(17, 19),
        AutoMigration(16, 19),
        AutoMigration(19, 20),
        AutoMigration(18, 20),
        AutoMigration(17, 20),
        AutoMigration(20, 21),
        AutoMigration(19, 21),
        AutoMigration(18, 21),
        AutoMigration(21, 22),
        AutoMigration(20, 22),
        AutoMigration(19, 22),
<<<<<<< HEAD
=======
        // New: adds Index on song(liked, downloadState, totalPlayTime, inLibrary),
        // album(liked, downloadState) and playlist(liked, downloadState). Pure index
        // additions are schema-additive — Room's AutoMigration handles them with no
        // manual Migration class needed.
        AutoMigration(22, 23),
>>>>>>> 56d2aea (fix)
    ],
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun getDatabaseDao(): DatabaseDao
}

expect fun getDatabaseBuilder(converters: Converters): RoomDatabase.Builder<MusicDatabase>

expect fun getDatabasePath(): String