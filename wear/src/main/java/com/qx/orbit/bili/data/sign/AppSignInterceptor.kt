package com.qx.orbit.bili.data.sign

import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

class AppSignInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val hasAppSign = invocation?.method()?.isAnnotationPresent(AppSign::class.java) == true

        if (!hasAppSign) return chain.proceed(request)

        val originalBody = request.body as? FormBody ?: return chain.proceed(request)
        val params = mutableMapOf<String, String>()
        for (i in 0 until originalBody.size) {
            params[originalBody.name(i)] = originalBody.value(i)
        }

        val signed = AppSignUtil.sign(params)
        val newBody = FormBody.Builder().apply {
            signed.forEach { (k, v) -> add(k, v) }
        }.build()

        val newRequest = request.newBuilder()
            .post(newBody)
            .header("User-Agent", AppSignUtil.HD_USER_AGENT)
            .build()

        return chain.proceed(newRequest)
    }
}
