package com.wavora.app.expect

/** Starts the platform-appropriate update flow for a new release whose
 * installer/APK lives at [downloadUrl] (a GitHub release asset URL - see
 * `GithubResponse`/`Asset` in kotlinYtmusicScraper).
 *
 * - Android: downloads the APK and hands it to the system package
 *   installer. Android does not allow a fully silent install outside of
 *   Play Store or a device-owner app, so this still needs one tap on a
 *   system prompt - but no browser round-trip, no manual APK hunting.
 *   [sha256] is ignored here - Android's PackageInstaller already
 *   verifies the APK's own signature, so a separate hash check isn't
 *   needed on that platform.
 * - Desktop: tries Conveyor's own update mechanism first; only if that
 *   isn't available does it fall back to the bundled WavoraUpdater,
 *   which uses [sha256] (when present) to verify the downloaded zip
 *   before installing it - see AppUpdate.jvm.kt's doc for the full flow.
 *
 * [versionName] is used only for the downloaded file name / any
 * progress notification text, not for version comparison (that already
 * happened before this is called). [sha256] is the lowercase hex SHA-256
 * of the asset at [downloadUrl] when GitHub published one (see
 * `Asset.digest`), null otherwise.
 *
 * [onError] is invoked when this function itself cannot even hand off
 * the update (e.g. Desktop: the user declined the UAC prompt, the
 * bundled updater couldn't be extracted, no permissions, no disk space -
 * see AppUpdate.jvm.kt's ensureUpdaterInstalled()/launchUpdaterElevated()).
 * It is NOT invoked for failures that happen later, inside a UI that
 * already has its own error display (e.g. WavoraUpdater.exe's own
 * download/verify/install failures) - only for failures in this
 * function's own synchronous work, which otherwise had no way to reach
 * the caller. Default no-op so existing callers aren't required to
 * handle it. */
expect fun installUpdate(
    downloadUrl: String,
    versionName: String,
    sha256: String? = null,
    onError: (String) -> Unit = {},
)

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
