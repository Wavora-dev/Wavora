package com.wavora.appdata.parser.search

import com.wavora.domain.model.model.searchResult.songs.Album
import com.wavora.domain.model.model.searchResult.songs.SongsResult
import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.scraper.models.SongItem
import com.wavora.scraper.pages.SearchResult

internal fun parseSearchSong(result: SearchResult): ArrayList<SongsResult> {
    val songsResult: ArrayList<SongsResult> = arrayListOf()
    result.items.forEach {
        val song = it as SongItem
        songsResult.add(
            SongsResult(
                album =
                    if (song.album != null) {
                        Album(
                            id = song.album!!.id,
                            name = song.album!!.name,
                        )
                    } else {
                        null
                    },
                artists =
                    song.artists.map { artistItem ->
                        com.wavora.domain.model.model.searchResult.songs.Artist(
                            id = artistItem.id,
                            name = artistItem.name,
                        )
                    },
                category = "Song",
                duration = if (song.duration != null) "%02d:%02d".format(song.duration!! / 60, song.duration!! % 60) else "",
                durationSeconds = song.duration ?: 0,
                feedbackTokens = null,
                isExplicit = song.explicit,
                resultType = "Song",
                thumbnails = listOf(Thumbnail(544, Regex("([wh])120").replace(song.thumbnail, "$1544"), 544)),
                title = song.title,
                videoId = song.id,
                videoType = "Song",
                year = "",
            ),
        )
    }
    return songsResult
}