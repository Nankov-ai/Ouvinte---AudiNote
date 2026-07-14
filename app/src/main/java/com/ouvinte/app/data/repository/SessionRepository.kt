package com.ouvinte.app.data.repository

import com.google.gson.Gson
import com.ouvinte.app.data.local.dao.SessionDao
import com.ouvinte.app.data.local.entity.*
import com.ouvinte.app.data.remote.dto.*
import com.ouvinte.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val gson: Gson
) {
    fun getAllSessions(): Flow<List<Session>> =
        dao.getAllSessions().map { entities -> entities.map { it.toDomain() } }

    suspend fun getSessionById(id: Long): Session? =
        dao.getSessionById(id)?.toDomain()

    suspend fun createSession(name: String, audioFilePath: String): Long {
        val entity = SessionEntity(
            name = name,
            createdAt = System.currentTimeMillis(),
            audioFilePath = audioFilePath,
            status = SessionStatus.RECORDED.name
        )
        return dao.insertSession(entity)
    }

    suspend fun updateSessionStatus(id: Long, status: SessionStatus) =
        dao.updateSessionStatus(id, status.name)

    suspend fun updateRecordingInfo(id: Long, durationSeconds: Int, speakerCount: Int) =
        dao.updateSessionRecordingInfo(id, durationSeconds, speakerCount)

    suspend fun saveGeminiFile(id: Long, uri: String, name: String) =
        dao.updateGeminiFile(id, uri, name)

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun saveTranscriptionResult(sessionId: Long, result: TranscriptionResult) {
        val speakers = result.speakers.mapIndexed { idx, s ->
            SpeakerEntity(
                sessionId = sessionId,
                label = s.id,
                displayName = s.name,
                isMain = s.isMain || idx == 0
            )
        }
        dao.insertSpeakers(speakers)

        val savedSpeakers = dao.getSpeakersForSession(sessionId)
        val speakerIdMap = savedSpeakers.associate { it.label to it.id }

        val segments = result.segments.map { seg ->
            TranscriptSegmentEntity(
                sessionId = sessionId,
                speakerId = speakerIdMap[seg.speakerId] ?: 0L,
                speakerLabel = seg.speakerId,
                text = seg.text,
                timestampSeconds = parseTimestamp(seg.timestamp)
            )
        }
        dao.insertTranscriptSegments(segments)

        updateSessionStatus(sessionId, SessionStatus.TRANSCRIBED)
        updateRecordingInfo(sessionId, 0, result.speakers.size)
    }

    suspend fun saveAnalysisResult(sessionId: Long, result: AnalysisResult) {
        val entity = AnalysisEntity(
            sessionId = sessionId,
            summary = result.summary,
            factChecksJson = gson.toJson(result.factCheck),
            sourcesJson = gson.toJson(result.sources)
        )
        dao.insertAnalysis(entity)
        updateSessionStatus(sessionId, SessionStatus.ANALYSED)
    }

    suspend fun saveQuestions(sessionId: Long, result: QuestionsResult, batch: Int) {
        val speakers = dao.getSpeakersForSession(sessionId)
        val speakerIdMap = speakers.associate { it.label to it.id }
        val speakerNameMap = speakers.associate { it.label to it.displayName }

        val entities = result.questions.map { q ->
            QuestionEntity(
                sessionId = sessionId,
                speakerId = q.speakerId?.let { speakerIdMap[it] },
                speakerLabel = q.speakerId?.let { speakerNameMap[it] },
                text = q.text,
                type = q.type,
                batch = batch
            )
        }
        dao.insertQuestions(entities)
    }

    suspend fun getFullSession(sessionId: Long): FullSession? {
        val session = dao.getSessionById(sessionId) ?: return null
        val speakers = dao.getSpeakersForSession(sessionId)
        val segments = dao.getTranscriptForSession(sessionId)
        val analysis = dao.getAnalysisForSession(sessionId)
        val questions = dao.getQuestionsForSession(sessionId)

        return FullSession(
            session = session.toDomain(),
            speakers = speakers.map { it.toDomain() },
            segments = segments.map { it.toDomain() },
            analysis = analysis?.toDomain(gson),
            questions = questions.map { it.toDomain() }
        )
    }

    private fun parseTimestamp(ts: String): Int {
        val parts = ts.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
    }
}

data class FullSession(
    val session: Session,
    val speakers: List<Speaker>,
    val segments: List<TranscriptSegment>,
    val analysis: Analysis?,
    val questions: List<Question>
)

// ── Extension mappers ─────────────────────────────────────────────────────────

fun SessionEntity.toDomain() = Session(
    id = id,
    name = name,
    createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault()),
    durationSeconds = durationSeconds,
    audioFilePath = audioFilePath,
    status = SessionStatus.valueOf(status),
    speakerCount = speakerCount,
    geminiFileUri = geminiFileUri,
    geminiFileName = geminiFileName,
    projectId = projectId
)

fun SpeakerEntity.toDomain() = Speaker(id, sessionId, label, displayName, isMain)

fun TranscriptSegmentEntity.toDomain() =
    TranscriptSegment(id, sessionId, speakerId, speakerLabel, text, timestampSeconds)

fun AnalysisEntity.toDomain(gson: Gson): Analysis {
    val factChecks = gson.fromJson(factChecksJson, Array<com.ouvinte.app.data.remote.dto.FactCheckResult>::class.java)
        ?.map { FactCheck(it.claim, FactVerdict.valueOf(it.verdict.uppercase()), it.evidence) }
        ?: emptyList()
    val sources = gson.fromJson(sourcesJson, Array<SourceResult>::class.java)
        ?.map { Source(it.title, it.url, it.description) }
        ?: emptyList()
    return Analysis(id, sessionId, summary, factChecks, sources)
}

fun QuestionEntity.toDomain() = Question(
    id, sessionId, speakerId, speakerLabel, text,
    QuestionType.valueOf(type.uppercase()), batch
)
