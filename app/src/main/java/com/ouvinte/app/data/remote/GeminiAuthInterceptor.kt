package com.ouvinte.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class GeminiAuthInterceptor(private var apiKey: String) : Interceptor {

    fun updateKey(key: String) { apiKey = key }

    override fun intercept(chain: Interceptor.Chain): Response {
        val key = apiKey
        val original = chain.request()
        val request = if (key.isNotBlank()) {
            original.newBuilder()
                .url(original.url.newBuilder().addQueryParameter("key", key).build())
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
