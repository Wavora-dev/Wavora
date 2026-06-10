package com.wavora.domain.model.model.intent

import com.eygraber.uri.Uri

data class GenericIntent(
    val action: String? = null,
    val data: Uri? = null,
    val type: String? = null
)