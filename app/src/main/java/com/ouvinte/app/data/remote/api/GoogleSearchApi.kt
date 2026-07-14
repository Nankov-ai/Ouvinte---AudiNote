package com.ouvinte.app.data.remote.api

import com.ouvinte.app.data.remote.dto.SearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleSearchApi {

    @GET("customsearch/v1")
    suspend fun search(
        @Query("key") apiKey: String,
        @Query("cx") searchEngineId: String,
        @Query("q") query: String,
        @Query("num") num: Int = 5,
        @Query("lr") language: String = "lang_pt",
        @Query("safe") safe: String = "active"
    ): SearchResponse
}
