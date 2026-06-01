package com.velaphi.journeytracker.core.network

import retrofit2.HttpException
import java.io.IOException

fun <T> Result<T>.mapTflErrors(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { Result.failure(IllegalStateException(it.toUserFacingMessage(), it)) },
)

fun Throwable.toUserFacingMessage(): String = when (this) {
    is HttpException -> when (code()) {
        404 -> "The requested TfL resource was not found. Check the line or location and try again."
        429 -> "TfL API rate limit reached. Please wait a moment and try again."
        in 500..599 -> "TfL service is temporarily unavailable. Please try again later."
        else -> "TfL API error (${code()}). ${message()}"
    }

    is IOException -> "Network error. Check your connection and try again."
    else -> message ?: "Something went wrong"
}
