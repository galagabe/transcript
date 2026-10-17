package com.phequals7.muesli.data

import android.content.Context
import android.content.SharedPreferences
import com.phequals7.muesli.data.entity.CustomWord
import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.data.entity.SpeakerName
import com.phequals7.muesli.data.entity.Transcript
import com.phequals7.muesli.data.entity.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SharedStore(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val customWordDao = database.customWordDao()
    private val dictationResultDao = database.dictationResultDao()
    private val recordingSessionDao = database.recordingSessionDao()
    private val transcriptDao = database.transcriptDao()
    private val speakerDao = database.speakerDao()

    private val prefs: SharedPreferences = context.getSharedPreferences("muesli_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ENGINE_TYPE = "engine_type" // legacy; always "sherpa" now
        private const val KEY_FILLER_WORD_REMOVAL = "filler_word_removal"
        private const val KEY_CUSTOM_DICTIONARY = "custom_dictionary"
        private const val KEY_USER_PROFILE_NAME = "user_profile_name"
        private const val KEY_DEFAULT_MEETING_TEMPLATE = "default_meeting_template"
        private const val KEY_RETAIN_MEETING_AUDIO = "retain_meeting_audio"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_SPEAKER_LABELS = "speaker_labels"
        private const val KEY_MIC_PREFERENCE = "mic_preference" // "auto", "phone", "bluetooth", "usb"
    }

    // Onboarding settings
    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var engineType: String
        // Only the on-device sherpa engine exists now (the "system" and
        // "mock" engines were removed); legacy stored values are ignored.
        get() = "sherpa"
        set(value) = prefs.edit().putString(KEY_ENGINE_TYPE, value).apply()

    var isFillerWordRemovalEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILLER_WORD_REMOVAL, true)
        set(value) = prefs.edit().putBoolean(KEY_FILLER_WORD_REMOVAL, value).apply()

    var isCustomDictionaryEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_DICTIONARY, true)
        set(value) = prefs.edit().putBoolean(KEY_CUSTOM_DICTIONARY, value).apply()

    /** Default meeting template preselected on the Meetings tab. */
    var defaultMeetingTemplate: String
        get() = prefs.getString(KEY_DEFAULT_MEETING_TEMPLATE, "general") ?: "general"
        set(value) = prefs.edit().putString(KEY_DEFAULT_MEETING_TEMPLATE, value).apply()

    /** When false, meeting audio is not retained on disk (text only). */
    var retainMeetingAudio: Boolean
        get() = prefs.getBoolean(KEY_RETAIN_MEETING_AUDIO, true)
        set(value) = prefs.edit().putBoolean(KEY_RETAIN_MEETING_AUDIO, value).apply()

    var userProfileName: String
        get() = prefs.getString(KEY_USER_PROFILE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PROFILE_NAME, value).apply()

    /** Selected on-device ASR model (id from the SpeechModels catalog). */
    var selectedModelId: String
        get() = prefs.getString(KEY_SELECTED_MODEL_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_MODEL_ID, value).apply()

    /** When on, meetings are diarized after stop and transcript chunks are
     * prefixed with speaker labels (adds post-processing time). */
    var speakerLabelsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPEAKER_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAKER_LABELS, value).apply()

    /** Recording microphone route: "auto" (default), "phone", "bluetooth", or "usb"
     * (AudioInputRouteManager.MicPreference ids, iOS RecordingMicrophonePreference parity). */
    var micPreference: String
        get() = prefs.getString(KEY_MIC_PREFERENCE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_MIC_PREFERENCE, value).apply()

    // Custom Words dictionary
    fun getCustomWordsFlow(): Flow<List<CustomWord>> = customWordDao.getAllCustomWordsFlow()
    suspend fun getCustomWords(): List<CustomWord> = customWordDao.getAllCustomWords()
    suspend fun insertCustomWord(word: CustomWord) = customWordDao.insertCustomWord(word)
    suspend fun updateCustomWord(word: CustomWord) = customWordDao.updateCustomWord(word)
    suspend fun deleteCustomWordById(id: String) = customWordDao.deleteCustomWordById(id)

    // Dictation results
    fun getDictationResultsFlow(): Flow<List<DictationResult>> = dictationResultDao.getAllResultsFlow()
    suspend fun getDictationResults(): List<DictationResult> = dictationResultDao.getAllResults()
    suspend fun getDictationResultForRequest(requestId: String): DictationResult? = dictationResultDao.getResultForRequest(requestId)
    suspend fun insertDictationResult(result: DictationResult) = dictationResultDao.insertResult(result)
    suspend fun deleteDictationResult(id: String) = dictationResultDao.deleteResult(id)

    // Recording Sessions
    fun getRecordingSessionsFlow(): Flow<List<RecordingSession>> = recordingSessionDao.getAllSessionsFlow()
    /** Sessions of one kind (RecordingSession.KIND_MEETING / KIND_VOICE_NOTE), newest first. */
    fun getRecordingSessionsFlow(kind: String): Flow<List<RecordingSession>> = recordingSessionDao.getSessionsByKindFlow(kind)
    suspend fun getRecordingSessions(): List<RecordingSession> = recordingSessionDao.getAllSessions()
    suspend fun getRecordingSessionById(id: String): RecordingSession? = recordingSessionDao.getSessionById(id)
    fun getRecordingSessionFlow(id: String): Flow<RecordingSession?> = recordingSessionDao.getSessionByIdFlow(id)

    /** Deletes a session with its transcript, speakers and its retained WAV (if any). */
    suspend fun deleteRecordingSessionFully(session: RecordingSession) {
        transcriptDao.getTranscriptForSession(session.id)?.let { transcriptDao.deleteTranscript(it) }
        speakerDao.deleteSegments(session.id)
        speakerDao.deleteSpeakerNames(session.id)
        session.audioFileName?.let { name ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                java.io.File(context.filesDir, "meetings/$name").delete()
            }
        }
        recordingSessionDao.deleteSession(session)
    }
    suspend fun getRecordingSessionByRequestId(requestId: String): RecordingSession? = recordingSessionDao.getSessionByRequestId(requestId)
    suspend fun insertRecordingSession(session: RecordingSession) = recordingSessionDao.insertSession(session)
    suspend fun updateRecordingSession(session: RecordingSession) = recordingSessionDao.updateSession(session)
    suspend fun deleteRecordingSession(session: RecordingSession) = recordingSessionDao.deleteSession(session)

    // Transcripts
    fun getTranscriptForSessionFlow(sessionId: String): Flow<Transcript?> = transcriptDao.getTranscriptForSessionFlow(sessionId)
    suspend fun getTranscriptForSession(sessionId: String): Transcript? = transcriptDao.getTranscriptForSession(sessionId)
    suspend fun insertTranscript(transcript: Transcript) = transcriptDao.insertTranscript(transcript)
    suspend fun deleteTranscript(transcript: Transcript) = transcriptDao.deleteTranscript(transcript)

    // Speakers (lot 3): diarized segments and user-given names
    suspend fun replaceTranscriptSegments(sessionId: String, segments: List<TranscriptSegment>) =
        speakerDao.replaceSegments(sessionId, segments)
    /** Distinct speaker numbers of a meeting's segments (empty for meetings recorded before lot 3). */
    fun getSegmentSpeakersFlow(sessionId: String): Flow<List<Int>> = speakerDao.getSegmentSpeakersFlow(sessionId)
    fun getSpeakerNamesFlow(sessionId: String): Flow<List<SpeakerName>> = speakerDao.getSpeakerNamesFlow(sessionId)
    /** Speaker number → name; only speakers the user actually renamed. */
    suspend fun getSpeakerNames(sessionId: String): Map<Int, String> =
        speakerDao.getSpeakerNames(sessionId).associate { it.speaker to it.name }
    /** Saves the names of a meeting's speakers; a blank name goes back to "Intervenant N". */
    suspend fun saveSpeakerNames(sessionId: String, names: Map<Int, String>) =
        speakerDao.replaceSpeakerNames(
            sessionId,
            names.map { (speaker, name) -> SpeakerName(sessionId, speaker, name.trim()) },
        )
}
