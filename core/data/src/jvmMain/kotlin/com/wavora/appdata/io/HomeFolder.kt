package com.wavora.appdata.io

import java.io.File

fun getHomeFolderPath(
    additionalPath: List<String>
): String = System.getProperty("user.home").let {
    if (additionalPath.isEmpty()) {
        it
    } else {
        buildString {
            append(it)
            additionalPath.forEach { path ->
                append(File.separator).append(path)
            }
        }
    }
}