package com.ouvinte.app.data.repository

import com.ouvinte.app.data.remote.api.GoogleSearchApi
import com.ouvinte.app.data.remote.dto.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val searchApi: GoogleSearchApi,
    private val settingsRepository: SettingsRepository
) {
    suspend fun searchTopics(topics: List<String>): Result<List<SearchItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = settingsRepository.getSearchApiKey()
                val engineId = settingsRepository.getSearchEngineId()
                if (apiKey.isBlank() || engineId.isBlank()) return@runCatching emptyList()
                val query = topics.take(3).joinToString(" ")
                val response = searchApi.search(
                    apiKey = apiKey,
                    searchEngineId = engineId,
                    query = query,
                    num = 5
                )
                response.items ?: emptyList()
            }
        }
}
