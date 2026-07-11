package com.wavora.appdata.di

import com.wavora.common.Config
import com.wavora.appdata.mediaservice.createMediaServiceHandler
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.session.PlayerController
import com.wavora.domain.mediaservice.session.PlayerControllerAdapter
import com.wavora.domain.mediaservice.session.PlayerSession
import com.wavora.domain.mediaservice.session.PlayerSessionAdapter
import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val mediaHandlerModule =
    module {
        single<MediaPlayerHandler>(createdAtStart = true) {
            createMediaServiceHandler(
                dataStoreManager = get(),
                songRepository = get(),
                streamRepository = get(),
                localPlaylistRepository = get(),
                analyticsRepository = get(),
                coroutineScope = get<CoroutineScope>(named(Config.SERVICE_SCOPE)),
            )
        }

        // Phase 1/2 of the PlayerSession migration (see PROMPT_01). Purely additive: nothing
        // reads from these yet, and MediaPlayerHandler above is completely untouched.
        // Bound as both the concrete adapter (MainActivity/DesktopApp need `reportError`) and
        // the PlayerSession/PlayerController interfaces (what ViewModels should depend on, per
        // point 5 of PROMPT_01 — depend on the abstraction, not the adapter). `bind` maps both
        // types to the SAME singleton instance, it doesn't create a second one.
        single {
            PlayerSessionAdapter(
                handler = get(),
                dataStoreManager = get(),
                scope = get<CoroutineScope>(named(Config.SERVICE_SCOPE)),
            )
        }.bind<PlayerSession>()

        single {
            PlayerControllerAdapter(
                handler = get(),
                scope = get<CoroutineScope>(named(Config.SERVICE_SCOPE)),
            )
        }.bind<PlayerController>()
    }