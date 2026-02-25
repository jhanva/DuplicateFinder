package com.duplicatefinder.domain.model

import android.content.IntentSender

class UserConfirmationRequiredException(
    val intentSender: IntentSender,
    message: String = "User confirmation required to delete media."
) : Exception(message)
