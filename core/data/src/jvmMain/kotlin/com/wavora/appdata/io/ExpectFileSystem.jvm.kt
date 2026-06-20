package com.wavora.appdata.io

import okio.FileSystem
import java.io.File


actual fun fileSystem(): FileSystem {
    return FileSystem.SYSTEM
}

actual fun fileDir(): String = File(getHomeFolderPath(listOf(".wavora"))).absolutePath