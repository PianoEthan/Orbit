package com.qx.orbit.bili.data.remote

import com.google.gson.JsonElement
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class ResultCallAdapterFactory : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) return null

        val callType = getParameterUpperBound(0, returnType as ParameterizedType)
        if (getRawType(callType) != Result::class.java) return null

        return ResultCallAdapter()
    }

    private class ResultCallAdapter : CallAdapter<JsonElement, Call<Result<JsonElement>>> {

        override fun responseType(): Type = JsonElement::class.java

        override fun adapt(call: Call<JsonElement>): Call<Result<JsonElement>> {
            return ResultCall(call)
        }
    }
}
