package com.ouvinte.app.data.remote.api

import com.ouvinte.app.data.remote.dto.GeminiFileInfo
import com.ouvinte.app.data.remote.dto.GeminiFileUploadResponse
import okhttp3.RequestBody
import retrofit2.http.*

interface GeminiFilesApi {

    @POST("upload/v1beta/files")
    @Headers("X-Goog-Upload-Protocol: raw")
    suspend fun uploadFile(
        @Header("X-Goog-Upload-Header-Content-Type") mimeType: String,
        @Header("X-Goog-Upload-Header-Content-Length") contentLength: Long,
        @Body body: RequestBody
    ): GeminiFileUploadResponse

    // getFile devolve GeminiFileInfo directamente (sem wrapper "file")
    @GET("v1beta/{name}")
    suspend fun getFile(
        @Path("name", encoded = true) name: String
    ): GeminiFileInfo
}
