package com.wavora.domain.model.model.mood

data class Mood(
    val genres: ArrayList<Genre>,
    val moodsMoments: ArrayList<MoodsMoment>,
)