package com.wavora.domain.model.model.searchResult

import com.wavora.domain.model.type.SearchResultType

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<SearchResultType>,
)