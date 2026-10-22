package fr.clinitex.transcription.voxtral

import fr.clinitex.transcription.summaries.ClinitexSummaryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Voxtral variant: sends one audio tranche to the Clinitex n8n webhook,
 * which forwards it to Mistral (`POST /v1/audio/transcriptions`, model
 * [MODEL_ID]) with the Mistral key held in n8n, and returns Mistral's JSON
 * as is. No Mistral key in the APK, only the device token.
 *
 * Multipart fields mirror Mistral's: file, language, diarize,
 * timestamp_granularities, context_bias (once per term), plus sessionId and
 * tranche for the n8n logs. Pure JVM so it runs against MockWebServer.
 */
class VoxtralClient(
    private val endpointUrl: String,
    private val deviceToken: String,
    private val http: OkHttpClient = defaultHttpClient(),
) {

    companion object {
        /** Mistral batch transcription model (Voxtral 3 family, public preview in October 2026). */
        const val MODEL_ID = "voxtral-mini-transcribe-3"

        /** Mistral caps the custom vocabulary with the audio in one context window: keep it short. */
        const val MAX_CONTEXT_TERMS = 100

        private val FLAC = "audio/flac".toMediaType()

        private val json = Json { ignoreUnknownKeys = true }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // ≈10 MB up on a mobile connection, then Mistral transcribes 10 minutes.
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    @Serializable
    data class Segment(
        val text: String = "",
        val start: Float = 0f,
        val end: Float = 0f,
        @SerialName("speaker_id") val speakerId: String? = null,
    )

    @Serializable
    data class Response(
        val text: String = "",
        val segments: List<Segment> = emptyList(),
    )

    /** Same retry rule as the summaries: retryable when trying later can work. */
    class VoxtralException(message: String, val retryable: Boolean, cause: Throwable? = null) : Exception(message, cause)

    fun isConfigured(): Boolean = endpointUrl.isNotBlank()

    /**
     * @param flac the tranche, FLAC 16 kHz mono ([FlacEncoder])
     * @param contextBias dictionary terms (names, sigles) to help recognition
     */
    suspend fun transcribe(
        flac: ByteArray,
        sessionId: String,
        trancheIndex: Int,
        diarize: Boolean,
        contextBias: List<String> = emptyList(),
        language: String = "fr",
    ): Response = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw VoxtralException("L'URL du service de transcription n'est pas configurée.", retryable = false)
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "$sessionId-$trancheIndex.flac", flac.toRequestBody(FLAC))
            .addFormDataPart("model", MODEL_ID)
            .addFormDataPart("language", language)
            .addFormDataPart("diarize", diarize.toString())
            .addFormDataPart("timestamp_granularities", "segment")
            .addFormDataPart("sessionId", sessionId)
            .addFormDataPart("tranche", trancheIndex.toString())
            .apply { contextBias.take(MAX_CONTEXT_TERMS).forEach { addFormDataPart("context_bias", it) } }
            .build()

        val request = Request.Builder()
            .url(endpointUrl)
            .post(body)
            .header(ClinitexSummaryClient.TOKEN_HEADER, deviceToken)
            .build()

        val (code, text) = try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw VoxtralException("Service de transcription injoignable : ${e.message}", retryable = true, cause = e)
        }
        if (code !in 200..299) {
            throw VoxtralException(
                "Le service de transcription a répondu HTTP $code : ${text.take(300)}",
                retryable = code >= 500 || code == 408 || code == 429,
            )
        }
        try {
            json.decodeFromString(Response.serializer(), text)
        } catch (e: Exception) {
            throw VoxtralException("Réponse du service de transcription illisible.", retryable = false, cause = e)
        }
    }
}
