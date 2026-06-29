package com.qx.orbit.bili.data.remote

import okhttp3.*
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import com.qx.orbit.bili.data.sign.AppSignInterceptor

object HttpClient {
    private val ipv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return Dns.SYSTEM.lookup(hostname).filter { it.address.size == 4 }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .addInterceptor(RedirectInterceptor())
        .addInterceptor(AppSignInterceptor())
        .addInterceptor(DefaultHeadersInterceptor())
        .addInterceptor(CookieAddInterceptor())
        .addInterceptor(CookieSaveInterceptor())
        .dns(ipv4Dns)
        .pingInterval(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .build()

    private class RedirectInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val location = response.header("Location") ?: return response

            val isSslRedirect = try {
                !request.isHttps && URI(location).scheme.equals("https", true) &&
                        request.url.host.equals(URI(location).host, true)
            } catch (_: Exception) { false }

            if (response.isRedirect) {
                if (request.url.host == "b23.tv" && !isSslRedirect) {
                    return response
                }
                return chain.proceed(request.newBuilder().url(location).build())
            }
            return response
        }
    }

    private class DefaultHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val builder = request.newBuilder()
            if (request.header("User-Agent") == null) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
            }
            if (request.header("Referer") == null) {
                builder.header("Referer", "https://www.bilibili.com/")
            }
            return chain.proceed(builder.build())
        }
    }

    private class CookieAddInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val cookie = CookieManager.getCookie()
            if (cookie.isEmpty() || request.header("Cookie") != null) {
                return chain.proceed(request)
            }
            val newRequest = request.newBuilder()
                .addHeader("Cookie", cookie)
                .build()
            return chain.proceed(newRequest)
        }
    }

    private class CookieSaveInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val newCookies = response.headers("Set-Cookie")
            if (newCookies.isEmpty()) return response

            val cookieMap = mutableMapOf<String, String>()
            CookieManager.getCookie().split("; ").filter { it.contains("=") }.forEach { part ->
                val eqIdx = part.indexOf("=")
                cookieMap[part.substring(0, eqIdx)] = part.substring(eqIdx + 1)
            }

            for (newCookie in newCookies) {
                val domain = Regex("Domain=([^;]+)").find(newCookie)?.groupValues?.get(1)
                if (domain != null && !domain.endsWith("bilibili.com")) continue

                val clean = newCookie.substringBefore("; ")
                val eqIdx = clean.indexOf("=")
                if (eqIdx <= 0) continue
                cookieMap[clean.substring(0, eqIdx)] = clean.substring(eqIdx + 1)
            }

            CookieManager.setCookie(cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" })
            return response
        }
    }
}
