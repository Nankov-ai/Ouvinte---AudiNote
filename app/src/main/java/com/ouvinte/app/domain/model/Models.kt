package com.ouvinte.app.domain.model

import java.time.LocalDateTime

data class Project(
    val id: Long = 0,
    val name: String,
    val createdAt: LocalDateTime
)

data class Session(
    val id: Long = 0,
    val name: String,
    val createdAt: LocalDateTime,
    val durationSeconds: Int = 0,
    val audioFilePath: String = "",
    val status: SessionStatus = SessionStatus.RECORDED,
    val speakerCount: Int = 0,
    val geminiFileUri: String = "",
    val geminiFileName: String = "",
    val projectId: Long? = null
)

enum class SessionStatus {
    RECORDING, RECORDED, TRANSCRIBING, TRANSCRIBED, ANALYSING, ANALYSED
}

data class Speaker(
    val id: Long = 0,
    val sessionId: Long,
    val label: String,
    val displayName: String,
    val isMain: Boolean = false
)

data class TranscriptSegment(
    val id: Long = 0,
    val sessionId: Long,
    val speakerId: Long,
    val speakerLabel: String,
    val text: String,
    val timestampSeconds: Int = 0
)

data class Analysis(
    val id: Long = 0,
    val sessionId: Long,
    val summary: String,
    val factChecks: List<FactCheck>,
    val sources: List<Source>
)

data class FactCheck(
    val claim: String,
    val verdict: FactVerdict,
    val evidence: String
)

enum class FactVerdict { VERIFIED, UNVERIFIED, INCORRECT }

data class Source(
    val title: String,
    val url: String,
    val description: String
)

data class Question(
    val id: Long = 0,
    val sessionId: Long,
    val speakerId: Long?,
    val speakerLabel: String?,
    val text: String,
    val type: QuestionType,
    val batch: Int = 1
)

enum class QuestionType { CHALLENGE, FUTURE, DEEPEN }
