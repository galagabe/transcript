package fr.clinitex.transcription.meetings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.clinitex.transcription.data.entity.RecordingSession

/**
 * Shared state bridge between [MeetingRecorderService] (which owns the mic
 * and the transcription pipeline) and the Compose UI. The service pushes
 * updates here; screens observe them as snapshot state.
 */
object MeetingRecordingController {

    var activeSessionId by mutableStateOf<String?>(null)
        private set
    var meetingTitle by mutableStateOf("")
        private set
    /** RecordingSession.kind of the active capture (meeting or voice note). */
    var kind by mutableStateOf(RecordingSession.KIND_MEETING)
        private set
    var templateId by mutableStateOf(MeetingTemplate.GENERAL.id)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var isFinalizing by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableIntStateOf(0)
        private set
    var inputLevel by mutableFloatStateOf(0f)
        private set
    var partialText by mutableStateOf("")
        private set
    var liveTranscript by mutableStateOf("")
        private set
    /** Voxtral variant: transcribed online after the fact, no live transcript. */
    var isOnlineTranscription by mutableStateOf(false)
        private set

    val isActive: Boolean get() = isRecording || isFinalizing
    val isVoiceNote: Boolean get() = kind == RecordingSession.KIND_VOICE_NOTE

    fun start(
        context: Context,
        sessionId: String,
        title: String,
        templateId: String,
        kind: String = RecordingSession.KIND_MEETING,
        consentAt: Long,
    ) {
        val intent = Intent(context, MeetingRecorderService::class.java)
            .setAction(MeetingRecorderService.ACTION_START)
            .putExtra(MeetingRecorderService.EXTRA_SESSION_ID, sessionId)
            .putExtra(MeetingRecorderService.EXTRA_TITLE, title)
            .putExtra(MeetingRecorderService.EXTRA_TEMPLATE_ID, templateId)
            .putExtra(MeetingRecorderService.EXTRA_KIND, kind)
            .putExtra(MeetingRecorderService.EXTRA_CONSENT_AT, consentAt)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, MeetingRecorderService::class.java)
                .setAction(MeetingRecorderService.ACTION_STOP)
        )
    }

    fun discard(context: Context) {
        context.startService(
            Intent(context, MeetingRecorderService::class.java)
                .setAction(MeetingRecorderService.ACTION_DISCARD)
        )
    }

    // ── Called by the service ──────────────────────────────────────────

    fun onCaptureStarted(sessionId: String, title: String, kind: String, templateId: String, online: Boolean = false) {
        isOnlineTranscription = online
        activeSessionId = sessionId
        meetingTitle = title
        this.kind = kind
        this.templateId = templateId
        isRecording = true
        isFinalizing = false
        elapsedSeconds = 0
        inputLevel = 0f
        partialText = ""
        liveTranscript = ""
    }

    fun onProgress(elapsedSec: Int, level: Float, partial: String, transcriptSoFar: String) {
        elapsedSeconds = elapsedSec
        inputLevel = level
        partialText = partial
        liveTranscript = transcriptSoFar
    }

    fun onFinalizing() {
        isRecording = false
        isFinalizing = true
        inputLevel = 0f
    }

    fun onFinished() {
        isRecording = false
        isFinalizing = false
        activeSessionId = null
        meetingTitle = ""
        elapsedSeconds = 0
        inputLevel = 0f
        partialText = ""
        liveTranscript = ""
    }
}
