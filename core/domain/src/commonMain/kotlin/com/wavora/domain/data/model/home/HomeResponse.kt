package com.wavora.domain.model.model.home

import com.wavora.domain.model.model.home.chart.Chart
import com.wavora.domain.model.model.mood.Mood
import com.wavora.domain.utils.Resource

data class HomeResponse(
    val homeItem: Resource<ArrayList<HomeItem>>,
    val exploreMood: Resource<Mood>,
    val exploreChart: Resource<Chart>,
)