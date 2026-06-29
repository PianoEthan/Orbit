package com.qx.orbit.bili.data.remote

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: BilibiliApiException) : Result<Nothing>()
}

class BilibiliApiException(
    val code: Int,
    override val message: String?,
    val httpCode: Int = 0
) : Exception(message)
