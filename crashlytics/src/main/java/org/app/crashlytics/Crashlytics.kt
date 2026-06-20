package com.wavora.crashlytics

import android.content.Context
import android.util.Log
import com.wavora.domain.model.player.PlayerError
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

// Sent crash to Sentry
fun reportCrash(throwable: Throwable) {
    Sentry.captureException(throwable)
}

fun configCrashlytics(applicationContext: Context, dsn: String) {
    SentryAndroid.init(applicationContext) { options ->
        Log.d("Sentry", "dsn: $dsn")
        options.dsn = dsn
    }
}

fun pushPlayerError(error: PlayerError) {
    Sentry.withScope { scope ->
        Sentry.captureMessage("Player Error: ${error.message}, code: ${error.errorCode}, code name: ${error.errorCodeName}")
    }
}