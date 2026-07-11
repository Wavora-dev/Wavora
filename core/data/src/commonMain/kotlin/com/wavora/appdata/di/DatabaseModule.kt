package com.wavora.appdata.di

import DatabaseDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.wavora.appdata.dataStore.DataStoreManagerImpl
import com.wavora.appdata.dataStore.createDataStoreInstance
import com.wavora.appdata.db.Converters
import com.wavora.appdata.db.MusicDatabase
import com.wavora.appdata.db.datasource.AnalyticsDatasource
import com.wavora.appdata.db.datasource.LocalDataSource
import com.wavora.appdata.db.getDatabaseBuilder
import com.wavora.domain.manager.DataStoreManager
import com.wavora.scraper.YouTube
import com.wavora.spotify.Spotify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module
import com.wavora.aiservice.AiClient
import com.wavora.lyrics.LyricsManager
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
val databaseModule =
    module {
        single(createdAtStart = true) {
            Converters()
        }
        // Database
        single(createdAtStart = true) {
            getDatabaseBuilder(
                get<Converters>()
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        // DatabaseDao
        single(createdAtStart = true) {
            get<MusicDatabase>().getDatabaseDao()
        }
        // LocalDataSource
        single(createdAtStart = true) {
            LocalDataSource(get<DatabaseDao>())
        }
        // AnalyticsDatasource
        single(createdAtStart = true) {
            AnalyticsDatasource(get<DatabaseDao>())
        }
        // Datastore
        single(createdAtStart = true) {
            createDataStoreInstance()
        }
        // DatastoreManager
        single<DataStoreManager>(createdAtStart = true) {
            DataStoreManagerImpl(get<DataStore<Preferences>>())
        }

        // Move YouTube from Singleton to Koin DI
        single(createdAtStart = true) {
            YouTube()
        }

        // Spotify, AiClient and LyricsManager are feature-scoped (Spotify canvas/lyrics,
        // AI lyrics translation, community lyrics) and are never touched before the user
        // triggers one of those features. Removed createdAtStart so Koin builds them lazily,
        // on the first real get()/inject(), instead of during Application.onCreate(). Same
        // singleton semantics otherwise: still exactly one instance for the app's lifetime.
        //
        // NOTE: this alone does not defer their construction in practice today, because
        // CommonRepository and LyricsCanvasRepository (RepositoryModule.kt) are themselves
        // single(createdAtStart = true) and inject Spotify/AiClient/LyricsManager via
        // get() in their module block — so Koin still builds them eagerly as a side effect of
        // building those two repositories at startKoin() time. Fixing that requires touching
        // RepositoryModule.kt, intentionally left out of this change.
        single {
            Spotify()
        }

        single {
            AiClient()
        }

        // LyricsManager (formerly WavoraLyricsClient) now talks to the new Cloudflare
        // Workers backend via LyricsProviderRegistry/WavoraLyricsProvider instead of the
        // dead api-lyrics.wavora.org domain. See core/service/lyricsService's
        // LyricsManager.kt for the full chain.
        single {
            LyricsManager()
        }
    }