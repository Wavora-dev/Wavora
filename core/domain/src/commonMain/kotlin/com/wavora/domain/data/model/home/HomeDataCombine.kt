package com.wavora.domain.model.model.home

import com.wavora.domain.model.model.home.chart.Chart
import com.wavora.domain.model.model.mood.Mood
import com.wavora.domain.utils.Resource

data class HomeDataCombine(
    val home: Resource<Pair<String?, List<HomeItem>>>,
    val mood: Resource<Mood>,
    val chart: Resource<Chart>,
    val newRelease: Resource<List<HomeItem>>,
)