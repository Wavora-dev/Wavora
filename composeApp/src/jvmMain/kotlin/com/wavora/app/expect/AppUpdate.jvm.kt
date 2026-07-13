package com.wavora.app.expect

import com.wavora.logger.Logger

private const val TAG = "AppUpdate"

actual fun supportsInAppUpdate(): Boolean = false

/** Irrelevant on Desktop - Conveyor's updater doesn't go through the
 * GitHub-assets/APK-picking path at all (see [installUpdate]'s doc). */
actual fun currentDeviceAbis(): List<String> = emptyList()

/** No-op on purpose: Desktop's update path is Conveyor's own background
 * updater (`updates = background` in conveyor.conf), which runs outside
 * this app's process and doesn't need anything triggered from Kotlin
 * code - by the time the update dialog would call this, Conveyor has
 * likely already downloaded the new version in the background and will
 * apply it on the next app restart. Logged (not silently swallowed) so
 * an unexpected call here - the dialog's "in-app install" button
 * shouldn't even be visible on Desktop per [supportsInAppUpdate] - shows
 * up during debugging instead of vanishing quietly. */
actual fun installUpdate(downloadUrl: String, versionName: String) {
    Logger.d(TAG, "installUpdate($versionName) called on Desktop - no-op, Conveyor's background updater owns this")
}
