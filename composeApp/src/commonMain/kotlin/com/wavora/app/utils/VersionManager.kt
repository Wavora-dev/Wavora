package com.wavora.app.utils

import com.wavora.app.BuildKonfig

object VersionManager {
    private var versionName: String? = null

    fun initialize() {
        if (versionName == null) {
            versionName =
                try {
                    BuildKonfig.versionName
                } catch (_: Exception) {
                    String()
                }
        }
    }

    fun getVersionName(): String = removeDevSuffix(versionName ?: String())

    private fun removeDevSuffix(versionName: String): String {
        return if (versionName.endsWith("-dev")) {
            versionName.replace("-dev", "")
        } else {
            versionName
        }
    }

    /** Compares two dot-separated numeric version strings (an optional
     * leading "v" is tolerated and ignored on either side). Returns a
     * positive number if [a] is newer than [b], negative if older, 0 if
     * equal - or null if either string has a non-numeric segment, so the
     * caller can fall back to its own handling instead of this silently
     * treating an unparseable tag as "no update". */
    private fun compareVersions(a: String, b: String): Int? {
        val partsA = a.removePrefix("v").split(".").map { it.trim().toIntOrNull() }
        val partsB = b.removePrefix("v").split(".").map { it.trim().toIntOrNull() }
        if (partsA.any { it == null } || partsB.any { it == null }) return null
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val diff = (partsA.getOrNull(i) ?: 0) - (partsB.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    /** Whether [remoteTag] (a GitHub release tag, e.g. "v1.2.3") is
     * actually newer than the version currently running - not merely
     * *different*, which used to be the check here and could offer an
     * "update" to an equal or even older release (e.g. a rollback tag).
     * Falls back to plain inequality against the formatted "v<version>"
     * string if either side can't be parsed as dot-separated numbers, to
     * preserve the previous behavior for any tag format this doesn't
     * understand rather than hiding a real update. */
    fun isNewerVersion(remoteTag: String): Boolean {
        val current = getVersionName()
        val cmp = compareVersions(remoteTag, current)
        return cmp?.let { it > 0 } ?: (remoteTag != "v$current")
    }
}