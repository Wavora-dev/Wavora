package com.wavora.appdata.io

import com.wavora.appdata.db.documentDirectory
import okio.FileSystem

actual fun fileSystem(): FileSystem = FileSystem.SYSTEM
actual fun fileDir(): String = documentDirectory() + "/Wavora"