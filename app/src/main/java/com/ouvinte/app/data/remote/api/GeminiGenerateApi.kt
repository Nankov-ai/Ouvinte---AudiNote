package com.ouvinte.app.data.remote.api

import com.ouvinte.app.data.remote.dto.GeminiGenerateRequest
import com.ouvinte.app.data.remote.dto.GeminiGenerateResponse
import retrofit2.http.*

interface GeminiGenerateApi {

    // ?key= é adicionado automaticamente pelo GeminiAuthInterceptor
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse
}
