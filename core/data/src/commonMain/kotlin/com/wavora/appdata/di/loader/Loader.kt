package com.wavora.appdata.di.loader

import com.wavora.appdata.di.databaseModule
import com.wavora.appdata.di.mediaHandlerModule
import com.wavora.appdata.di.repositoryModule
import org.koin.core.context.loadKoinModules

fun loadAllModules() {
    loadKoinModules(
        listOf(
            databaseModule,
            repositoryModule,
        ),
    )
    loadKoinModules(mediaHandlerModule)
    loadMediaService()
}

expect fun loadMediaService()