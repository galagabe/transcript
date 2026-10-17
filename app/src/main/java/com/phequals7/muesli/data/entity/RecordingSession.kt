package com.phequals7.muesli.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recording_sessions")
data class RecordingSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val requestID: String? = null,
    val kind: String, // KIND_MEETING ("meeting") or KIND_VOICE_NOTE ("voiceNote"); legacy Muesli dictation kinds are unused
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val phase: String = "recording", // "recording", "transcribing", "completed", "failed", "cancelled"
    val audioFileName: String? = null,
    val transcriptID: String? = null,
    val engineIdentifier: String? = null,
    val errorMessage: String? = null,
    /** MeetingTemplatePreset id (general, oneOnOne, standup, ...). */
    val templateId: String = "general",
    /** Free-form notes written by the user during/after the meeting. */
    val manualNotes: String = "",
    /** AI-generated structured notes (or failure notes when generation fails). */
    val summaryText: String = "",
    /** "notStarted", "queued" (waiting for network), "generating", "completed", "failed", "unavailable". */
    val summaryState: String = "notStarted"
) {
    companion object {
        const val KIND_MEETING = "meeting"
        /** Short dictation cleaned up by the summary webhook (Vocaux tab). */
        const val KIND_VOICE_NOTE = "voiceNote"
    }
}
