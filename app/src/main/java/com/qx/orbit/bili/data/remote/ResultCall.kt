package com.qx.orbit.bili.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import android.util.Log
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ResultCall(
    private val delegate: Call<JsonElement>
) : Call<Result<JsonElement>> {

    companion object {
        private const val TAG = "BiliApi"
    }

    override fun enqueue(callback: Callback<Result<JsonElement>>) {
        delegate.enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                val result = mapResponse(response, call.request())
                callback.onResponse(this@ResultCall, Response.success(result))
            }

            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                Log.e(TAG, "Network error: ${call.request().url} - ${t.message}")
                val error = Result.Error(
                    BilibiliApiException(code = -2, message = t.message ?: "Network error")
                )
                callback.onResponse(this@ResultCall, Response.success(error))
            }
        })
    }

    override fun execute(): Response<Result<JsonElement>> {
        val response = delegate.execute()
        return Response.success(mapResponse(response, delegate.request()))
    }

    private fun mapResponse(response: Response<JsonElement>, request: Request): Result<JsonElement> {
        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code()}: ${request.url}")
            return Result.Error(
                BilibiliApiException(
                    code = -1,
                    message = "HTTP ${response.code()}",
                    httpCode = response.code()
                )
            )
        }

        val body = response.body()
        if (body == null) {
            Log.e(TAG, "Empty body: ${request.url}")
            return Result.Error(BilibiliApiException(code = -1, message = "Empty response body"))
        }

        if (body.isJsonObject) {
            val obj = body.asJsonObject
            val code = obj.get("code")?.asInt ?: 0
            if (code != 0) {
                val message = obj.get("message")?.asString ?: "Unknown error"
                Log.w(TAG, "API error code=$code: $message | ${request.url}")
                return Result.Error(BilibiliApiException(code = code, message = message))
            }
        }

        Log.d(TAG, "OK: ${request.url}")
        return Result.Success(body)
    }

    override fun isExecuted(): Boolean = delegate.isExecuted
    override fun cancel() = delegate.cancel()
    override fun isCanceled(): Boolean = delegate.isCanceled
    override fun clone(): Call<Result<JsonElement>> = ResultCall(delegate.clone())
    override fun request(): Request = delegate.request()
    override fun timeout(): Timeout = delegate.timeout()
}
