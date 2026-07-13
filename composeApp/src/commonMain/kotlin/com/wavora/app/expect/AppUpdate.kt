package com.wavora.app.expect

/** Starts the platform-appropriate update flow for a new release whose
 * installer/APK lives at [downloadUrl] (a GitHub release asset URL - see
 * `GithubResponse`/`Asset` in kotlinYtmusicScraper).
 *
 * - Android: downloads the APK and hands it to the system package
 *   installer. Android does not allow a fully silent install outside of
 *   Play Store or a device-owner app, so this still needs one tap on a
 *   system prompt - but no browser round-trip, no manual APK hunting.
 * - Desktop: no-op. Desktop updates are handled out-of-band by
 *   Conveyor's own background update check (`updates = background` in
 *   conveyor.conf) - there is nothing for the app itself to download or
 *   install, Conveyor already replaced the running app's files and will
 *   apply the update on next restart.
 *
 * [versionName] is used only for the downloaded file name / any
 * progress notification text, not for version comparison (that already
 * happened before this is called). */
expect fun installUpdate(downloadUrl: String, versionName: String)

/** Whether the update dialog's primary button should say "download and
 * install" (Android) versus something like "OK, se instalará solo"
 * (Desktop - see [installUpdate]'s doc for why there's nothing to do on
 * that platform). */
expect fun supportsInAppUpdate(): Boolean

/** Device's supported ABIs, most-preferred first (e.g. `["arm64-v8a",
 * "armeabi-v7a"]`). Used to auto-pick the right APK asset out of a release
 * that ships one per architecture (see `Wavora-arm64.apk`,
 * `Wavora-armeabi-v7a.apk`, `Wavora-x86_64.apk`, `Wavora-universal.apk` in
 * `.github/workflows/release.yml`). Empty on Desktop, where this is
 * irrelevant (see [installUpdate]'s doc). */
expect fun currentDeviceAbis(): List<String>
