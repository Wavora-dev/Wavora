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
)

data class ApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
)