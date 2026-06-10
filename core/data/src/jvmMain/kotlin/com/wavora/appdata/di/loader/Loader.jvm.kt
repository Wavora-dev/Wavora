package com.wavora.appdata.di.loader

import com.wavora.media_jvm.di.loadVlcModule

actual fun loadMediaService() {
    loadVlcModule()
}
