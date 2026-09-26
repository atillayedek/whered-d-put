package com.wheredidiputit.presentation.common

import androidx.annotation.StringRes
import com.wheredidiputit.R
import com.wheredidiputit.domain.model.AppError

/** Plain-language copy for every domain error. No codes, no stack traces. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.NETWORK -> R.string.error_network
    AppError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
    AppError.EMAIL_NOT_CONFIRMED -> R.string.error_email_not_confirmed
    AppError.EMAIL_IN_USE -> R.string.error_email_in_use
    AppError.WEAK_PASSWORD -> R.string.error_weak_password
    AppError.SAME_PASSWORD -> R.string.error_same_password
    AppError.RATE_LIMITED -> R.string.error_rate_limited
    AppError.EMAIL_RATE_LIMITED -> R.string.error_email_rate_limited
    AppError.SESSION_EXPIRED -> R.string.error_session_expired
    AppError.NOT_CONFIGURED -> R.string.error_not_configured
    AppError.PHOTO_UNSUPPORTED -> R.string.photo_unsupported
    AppError.PHOTO_TOO_LARGE -> R.string.photo_too_large
    AppError.NOT_FOUND -> R.string.error_not_found
    AppError.STORAGE -> R.string.error_storage
    AppError.ITEM_LIMIT_REACHED -> R.string.error_item_limit
    AppError.UNKNOWN -> R.string.error_generic
}
