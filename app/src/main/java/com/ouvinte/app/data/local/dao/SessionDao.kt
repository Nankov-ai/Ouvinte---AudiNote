package com.ouvinte.app.data.local.dao

import androidx.room.*
import com.ouvinte.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    // Sessions
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("UPDATE sessions SET status = :status WHERE id = :id")
    suspend fun updateSessionStatus(id: Long, status: String)

    @Query("UPDATE sessions SET durationSeconds = :duration, speakerCount = :speakerCount WHERE id = :id")
    suspend fun updateSessionRecordingInfo(id: Long, duration: Int, speakerCount: Int)

    @Query("UPDATE sessions SET geminiFileUri = :uri, geminiFileName = :name WHERE id = :id")
    suspend fun updateGeminiFile(id: Long, uri: String, name: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // Speakers
    @Query("SELECT * FROM speakers WHERE sessionId = :sessionId ORDER BY isMain DESC, label ASC")
    suspend fun getSpeakersForSession(sessionId: Long): List<SpeakerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakers(speakers: List<SpeakerEntity>)

    // Transcript
    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY timestampSeconds ASC")
    suspend fun getTranscriptForSession(sessionId: Long): List<TranscriptSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptSegments(segments: List<TranscriptSegmentEntity>)

    // Analysis
    @Query("SELECT * FROM analyses WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getAnalysisForSession(sessionId: Long): AnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: AnalysisEntity): Long

    // Questions
    @Query("SELECT * FROM questions WHERE sessionId = :sessionId ORDER BY batch ASC, id ASC")
    suspend fun getQuestionsForSession(sessionId: Long): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("SELECT MAX(batch) FROM questions WHERE sessionId = :sessionId")
    suspend fun getMaxBatchForSession(sessionId: Long): Int?
}
