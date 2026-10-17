package com.phequals7.muesli.summaries

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Clinitex meeting summaries: POSTs the transcript to the Clinitex n8n
 * webhook, which calls Claude server-side and returns `{title, notes}`.
 * No AI provider key ever ships in the APK — only a per-device token.
 *
 * Pure JVM (no android.* / org.json) so it runs under local unit tests
 * against MockWebServer.
 */
class ClinitexSummaryClient(
    private val endpointUrl: String,
    private val deviceToken: String,
    private val http: OkHttpClient = defaultHttpClient(),
) {

    companion object {
        const val TOKEN_HEADER = "X-Clinitex-Device-Token"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = true
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // n8n waits for Claude before answering; long meetings take a while.
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    /** Body of `POST {CLINITEX_SUMMARY_URL}` (contract in CLAUDE.md). */
    @Serializable
    data class SummaryRequest(
        val sessionId: String,
        val title: String,
        val templateId: String,
        val templateInstructions: String,
        /** Speaker number ("1", "2", …) → name given in the Transcription tab (lot 3). */
        val speakerNames: Map<String, String> = emptyMap(),
        val manualNotes: String = "",
        val transcript: String,
        val durationSec: Long,
        val appVersion: String,
        /** Reserved for a later delivery target (Outlook, Notion…); always null in v1. */
        val destination: String? = null,
    )

    @Serializable
    data class SummaryResponse(
        val title: String = "",
        val notes: String = "",
    )

    /**
     * @param retryable true when trying again later can succeed (network
     * down, 5xx, 408, 429); false for errors a retry will not fix
     * (bad token, malformed request, empty summary).
     */
    class SummaryException(
        message: String,
        val retryable: Boolean,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    /** True when the build was given a webhook URL. */
    fun isConfigured(): Boolean = endpointUrl.isNotBlank()

    suspend fun summarize(request: SummaryRequest): SummaryResponse = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw SummaryException("L'URL du service de synthèse n'est pas configurée.", retryable = false)
        }

        val httpRequest = Request.Builder()
            .url(endpointUrl)
            .post(json.encodeToString(SummaryRequest.serializer(), request).toRequestBody(JSON_MEDIA_TYPE))
            .header(TOKEN_HEADER, deviceToken)
            .build()

        val (code, body) = try {
            http.newCall(httpRequest).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw SummaryException("Service de synthèse injoignable : ${e.message}", retryable = true, cause = e)
        }

        if (code !in 200..299) {
            val retryable = code >= 500 || code == 408 || code == 429
            throw SummaryException(
                "Le service de synthèse a répondu HTTP $code : ${body.take(300)}",
                retryable = retryable,
            )
        }

        val parsed = try {
            json.decodeFromString(SummaryResponse.serializer(), body)
        } catch (e: Exception) {
            throw SummaryException("Réponse du service de synthèse illisible.", retryable = false, cause = e)
        }
        if (parsed.notes.isBlank()) {
            throw SummaryException("Le compte rendu reçu est vide.", retryable = false)
        }
        parsed
    }

    /** Kept as summaryText when generation gives up: the raw transcript is never lost. */
    fun failureNotes(
        transcript: String,
        meetingTitle: String,
        error: Throwable,
        manualNotes: String? = null,
    ): String = buildString {
        // Shared by meetings and voice notes: wording stays neutral.
        append("## Synthèse impossible\n\n").append(meetingTitle)
        append("\n\nLe texte mis au propre n'a pas pu être généré.\n\n")
        append(error.message ?: "Erreur inconnue")
        if (!manualNotes.isNullOrBlank()) {
            append("\n\n## Notes saisies\n\n").append(manualNotes.trim())
        }
        append("\n\n## Transcription brute\n\n").append(transcript)
    }
}
