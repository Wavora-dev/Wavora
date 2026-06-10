package com.wavora.scraper.models.response

enum class LikeStatus {
    LIKE,
    DISLIKE,
    INDIFFERENT,
}

fun String?.toLikeStatus(): LikeStatus =
    when (this) {
        "LIKE" -> LikeStatus.LIKE
        "DISLIKE" -> LikeStatus.DISLIKE
        else -> LikeStatus.INDIFFERENT
    }