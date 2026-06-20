package com.wavora.domain.repository

import com.wavora.domain.model.model.home.HomeItem
import com.wavora.domain.model.model.home.chart.Chart
import com.wavora.domain.model.model.mood.Mood
import com.wavora.domain.model.model.mood.genre.GenreObject
import com.wavora.domain.model.model.mood.moodmoments.MoodsMomentObject
import com.wavora.domain.utils.Resource
import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    /**
     * @return Pair of continueParams and HomeItem List
     */
    fun getHomeData(
        params: String? = null,
        viewString: String,
        songString: String,
    ): Flow<Resource<Pair<String?, List<HomeItem>>>>

    fun getHomeDataContinue(
        continueParam: String,
        viewString: String,
        songString: String,
    ): Flow<Resource<Pair<String?, List<HomeItem>>>>

    fun getNewRelease(
        newReleaseString: String,
        musicVideoString: String,
    ): Flow<Resource<List<HomeItem>>>

    fun getChartData(countryCode: String = "KR"): Flow<Resource<Chart>>

    fun getMoodAndMomentsData(): Flow<Resource<Mood>>

    fun getGenreData(params: String): Flow<Resource<GenreObject>>

    fun getMoodData(params: String): Flow<Resource<MoodsMomentObject>>
}