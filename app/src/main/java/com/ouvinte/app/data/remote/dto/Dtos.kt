package com.ouvinte.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Google Custom Search ──────────────────────────────────────────────────────

data class SearchResponse(
    val items: List<SearchItem>? = null
)

data class SearchItem(
    val title: String = "",
    val link: String = "",
    val snippet: String = ""
)

// ── Gemini Files API ──────────────────────────────────────────────────────────

data class GeminiFileUploadResponse(
    val file: GeminiFileInfo? = null
)

data class GeminiFileInfo(
    val name: String = "",
    val uri: String = "",
    val mimeType: String = "",
    val state: String = ""
)

// ── Gemini Generate Content (REST fallback) ───────────────────────────────────

data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    @SerializedName("inlineData")
    val inlineData: GeminiInlineData? = null,
    @SerializedName("fileData")
    val fileData: GeminiFileData? = null
)

data class GeminiInlineData(
    @SerializedName("mimeType") val mimeType: String,
    val data: String
)

data class GeminiFileData(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("fileUri") val fileUri: String
)

data class GeminiGenerationConfig(
    val temperature: Float = 0.2f,
    @SerializedName("responseMimeType")
    val responseMimeType: String? = null,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int = 65536
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerializedName("finishReason")
    val finishReason: String? = null
)

// ── Parsed AI responses ───────────────────────────────────────────────────────

data class TranscriptionResult(
    val speakers: List<SpeakerResult> = emptyList(),
    val segments: List<SegmentResult> = emptyList()
)

data class SpeakerResult(
    val id: String = "",
    val name: String = "",
    val isMain: Boolean = false
)

data class SegmentResult(
    val speakerId: String = "",
    val text: String = "",
    val timestamp: String = "00:00:00"
)

data class AnalysisResult(
    val topics: List<String> = emptyList(),
    val summary: String = "",
    @SerializedName("factCheck")
    val factCheck: List<FactCheckResult> = emptyList(),
    val sources: List<SourceResult> = emptyList()
)

data class FactCheckResult(
    val claim: String = "",
    val verdict: String = "UNVERIFIED",
    val evidence: String = ""
)

data class SourceResult(
    val title: String = "",
    val url: String = "",
    val description: String = ""
)

data class QuestionsResult(
    val questions: List<QuestionResult> = emptyList()
)

data class QuestionResult(
    val text: String = "",
    val speakerId: String? = null,
    val type: String = "DEEPEN"
)
