package fr.clinitex.transcription.data.dao

import androidx.room.*
import fr.clinitex.transcription.data.entity.RecordingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    fun getAllSessionsFlow(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions WHERE kind = :kind ORDER BY createdAt DESC")
    fun getSessionsByKindFlow(kind: String): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    suspend fun getAllSessions(): List<RecordingSession>

    @Query("SELECT * FROM recording_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): RecordingSession?

    @Query("SELECT * FROM recording_sessions WHERE id = :id LIMIT 1")
    fun getSessionByIdFlow(id: String): Flow<RecordingSession?>

    @Query("SELECT * FROM recording_sessions WHERE requestID = :requestId LIMIT 1")
    suspend fun getSessionByRequestId(requestId: String): RecordingSession?

    /** Lot 5 audio purge: targeted update so a concurrent edit of the row (notes, summary) is not overwritten. */
    @Query("UPDATE recording_sessions SET audioFileName = NULL, audioDeletedAt = :deletedAt WHERE id = :id")
    suspend fun markAudioDeleted(id: String, deletedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSession)

    @Update
    suspend fun updateSession(session: RecordingSession)

    @Delete
    suspend fun deleteSession(session: RecordingSession)
}
