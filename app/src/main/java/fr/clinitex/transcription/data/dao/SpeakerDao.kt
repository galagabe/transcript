package fr.clinitex.transcription.data.dao

import androidx.room.*
import fr.clinitex.transcription.data.entity.SpeakerName
import fr.clinitex.transcription.data.entity.TranscriptSegment
import kotlinx.coroutines.flow.Flow

/** Diarized transcript segments and user-given speaker names (lot 3). */
@Dao
interface SpeakerDao {
    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY startSec")
    suspend fun getSegments(sessionId: String): List<TranscriptSegment>

    @Query("SELECT DISTINCT speaker FROM transcript_segments WHERE sessionId = :sessionId ORDER BY speaker")
    fun getSegmentSpeakersFlow(sessionId: String): Flow<List<Int>>

    @Insert
    suspend fun insertSegments(segments: List<TranscriptSegment>)

    @Query("DELETE FROM transcript_segments WHERE sessionId = :sessionId")
    suspend fun deleteSegments(sessionId: String)

    @Transaction
    suspend fun replaceSegments(sessionId: String, segments: List<TranscriptSegment>) {
        deleteSegments(sessionId)
        if (segments.isNotEmpty()) insertSegments(segments)
    }

    @Query("SELECT * FROM speaker_names WHERE sessionId = :sessionId ORDER BY speaker")
    suspend fun getSpeakerNames(sessionId: String): List<SpeakerName>

    @Query("SELECT * FROM speaker_names WHERE sessionId = :sessionId ORDER BY speaker")
    fun getSpeakerNamesFlow(sessionId: String): Flow<List<SpeakerName>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakerNames(names: List<SpeakerName>)

    @Query("DELETE FROM speaker_names WHERE sessionId = :sessionId")
    suspend fun deleteSpeakerNames(sessionId: String)

    /** Replaces all names of a session; blank names are dropped (back to "Intervenant N"). */
    @Transaction
    suspend fun replaceSpeakerNames(sessionId: String, names: List<SpeakerName>) {
        deleteSpeakerNames(sessionId)
        val kept = names.filter { it.name.isNotBlank() }
        if (kept.isNotEmpty()) insertSpeakerNames(kept)
    }
}
