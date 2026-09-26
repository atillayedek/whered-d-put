package com.wheredidiputit.data.remote

import com.wheredidiputit.domain.model.AppError
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Maps any backend failure to a domain error. Details stay inside the data layer. */
internal fun Throwable.toAppError(): AppError = when (this) {
    is BackendNotConfiguredException -> AppError.NOT_CONFIGURED
    is HttpRequestException, is IOException -> AppError.NETWORK
    is RestException -> restError()
    else -> AppError.UNKNOWN
}

private fun RestException.restError(): AppError {
    val text = listOfNotNull(error, message).joinToString(" ").lowercase()
    return when {
        "email_not_confirmed" in text || "email not confirmed" in text -> AppError.EMAIL_NOT_CONFIRMED
        "invalid_credentials" in text || "invalid login credentials" in text || "invalid_grant" in text ->
            AppError.INVALID_CREDENTIALS
        "user_already_exists" in text || "email_exists" in text || "already registered" in text -> AppError.EMAIL_IN_USE
        "same_password" in text -> AppError.SAME_PASSWORD
        "weak_password" in text || "password should" in text -> AppError.WEAK_PASSWORD
        "over_email_send_rate_limit" in text || "email rate limit" in text -> AppError.EMAIL_RATE_LIMITED
        "rate_limit" in text || statusCode == 429 -> AppError.RATE_LIMITED
        "session_not_found" in text || "jwt expired" in text || statusCode == 401 -> AppError.SESSION_EXPIRED
        statusCode == 404 -> AppError.NOT_FOUND
        else -> AppError.UNKNOWN
    }
}

/**
 * Whether a sync failure is worth retrying later (connectivity, server
 * trouble, expired token) or is permanent for the record in question.
 */
internal fun Throwable.isTransient(): Boolean = when (this) {
    is HttpRequestException, is IOException -> true
    is RestException -> statusCode == 401 || statusCode == 408 || statusCode == 429 || statusCode >= 500
    else -> false
}

/** runCatching that never swallows coroutine cancellation. */
internal inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
