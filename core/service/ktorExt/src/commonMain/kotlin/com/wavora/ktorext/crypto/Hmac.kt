package com.wavora.ktorext.crypto

expect class Hmac(
    algorithm: String,
    secretKey: String,
) {
    fun getMacTimestampPair(uri: String): Pair<String, String>
    fun generateHmac(data: String): String
    fun validateHmac(
        data: String,
        hmac: String,
    ): Boolean
    fun isValidTimestamp(timestamp: String): Boolean
}

// HmacUri (BASE_HMAC_URI/TRANSLATED_HMAC_URI/VOTE_HMAC_URI/VOTE_TRANSLATED_HMAC_URI)
// used to live here. It was only ever consumed by the old WavoraLyrics/
// WavoraLyricsClient classes that signed requests to the now-dead
// api-lyrics.wavora.org domain. The new Cloudflare backend authenticates
// public writes by IP rate limiting only (see wavora-lyrics-backend's
// README, "Rate limiting"), so there is nothing left to sign and the
// object was removed as dead code during the backend migration.
//
// The `Hmac` expect/actual class above is a generic multiplatform crypto
// primitive, not specific to the old lyrics API - it currently has no
// other callers in this codebase, but was left in place since removing
// an expect/actual across 4 source sets (common/jvm/ios/android) is
// outside the scope of this integration and carries its own risk. If it
// stays unused, it's a good candidate for a future, separate cleanup.