package com.phequals7.muesli.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One decoded chunk of a diarized meeting with its speaker (lot 3). Written
 * once at the end of recording from the decoded chunks + diarization
 * segments; the readable transcript text stays in [Transcript].
 */
@Entity(tableName = "transcript_segments", indices = [Index("sessionId")])
data class TranscriptSegment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val startSec: Float,
    val endSec: Float,
    /** Speaker number as shown in the transcript ("Intervenant N"), from 1. */
    val speaker: Int,
    val text: String,
)
