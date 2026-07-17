package com.wavora.domain.model.model.update

data class UpdateData(
    val tagName: String,
    val releaseTime: String?,
    val body: String,
    // GitHub release asset URL for the Android APK, when this update
    // came from the GitHub channel and a .apk asset was actually
    // attached to the release. Null for the F-Droid channel (F-Droid's
    // own client owns updates there - self-installing over it would
    // confuse its update tracking) and whenever no matching asset is
    // found, in which case callers should fall back to opening the
    // releases page instead of assuming this is always present.
    val apkDownloadUrl: String? = null,
    // Every .apk asset attached to this release (name + url + size),
    // in the order GitHub returned them. Empty for the F-Droid channel.
    // The UI uses this to let the person pick manually when the caller
    // can't confidently match one to the device's architecture (see
    // AppUpdate.currentDeviceAbis()) - e.g. a release that for whatever
    // reason didn't ship the expected Wavora-<abi>.apk naming.
    val apkAssets: List<ApkAsset> = emptyList(),
    // GitHub release asset URL for AppwavoraWindows.zip - the bundle the
    // Windows fallback updater downloads when Conveyor's own update
    // mechanism isn't available (see AppUpdate.jvm.kt). Null when no
    // matching asset is attached to the release.
    val windowsZipDownloadUrl: String? = null,
    // SHA-256 of that same asset (lowercase hex, no "sha256:" prefix),
    // parsed from GitHub's own per-asset digest field. Null when GitHub
    // hasn't published one (older release) - WavoraUpdater skips
    // verification in that case rather than failing on its absence.
    val windowsZipSha256: String? = null,
)

data class ApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
)