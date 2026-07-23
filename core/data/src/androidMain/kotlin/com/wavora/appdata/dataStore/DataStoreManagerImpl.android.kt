package com.wavora.appdata.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.wavora.common.SETTINGS_FILENAME
import createDataStore
import org.koin.mp.KoinPlatform.getKoin
import java.util.Locale

actual fun createDataStoreInstance(): DataStore<Preferences> {
    return createDataStore(
        producePath = { getKoin().get<Context>().filesDir.resolve("datastore/$SETTINGS_FILENAME.preferences_pb").absolutePath }
    )
}

// Ver el comentario completo en la versión JVM de este archivo - mismo
// fix, misma razón (el fallback de idioma vive en código común
// compartido por ambas plataformas).
actual fun getSystemDefaultLanguageCode(): String = Locale.getDefault().toLanguageTag()