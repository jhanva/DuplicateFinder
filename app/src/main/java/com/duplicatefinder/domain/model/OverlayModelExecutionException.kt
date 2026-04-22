package com.duplicatefinder.domain.model

class OverlayModelExecutionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
