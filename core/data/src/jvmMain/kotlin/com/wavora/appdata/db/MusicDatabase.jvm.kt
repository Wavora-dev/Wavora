package com.wavora.appdata.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.wavora.common.DB_NAME
import com.wavora.appdata.io.getHomeFolderPath
import java.io.File

actual fun getDatabaseBuilder(
    converters: Converters
): RoomDatabase.Builder<MusicDatabase> {
    return Room.databaseBuilder<MusicDatabase>(
        name = getDatabasePath()
    ).addTypeConverter(converters)
}

actual fun getDatabasePath(): String {
    val dbFile = File(getHomeFolderPath(listOf(".wavora", "db")), DB_NAME)
    return dbFile.absolutePath
}