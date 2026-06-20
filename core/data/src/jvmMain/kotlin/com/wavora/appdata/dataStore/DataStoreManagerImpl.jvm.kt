package com.wavora.appdata.dataStore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.wavora.common.SETTINGS_FILENAME
import com.wavora.appdata.io.getHomeFolderPath
import createDataStore
import java.io.File

actual fun createDataStoreInstance(): DataStore<Preferences> = createDataStore(
    producePath = {
        val file = File(getHomeFolderPath(listOf(".wavora")), "$SETTINGS_FILENAME.preferences_pb")
        file.absolutePath
    }
)