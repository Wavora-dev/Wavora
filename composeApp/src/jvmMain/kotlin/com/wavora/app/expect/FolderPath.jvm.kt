package com.wavora.app.expect

actual fun getDownloadFolderPath(): String = System.getProperty("user.home") + "/Downloads"