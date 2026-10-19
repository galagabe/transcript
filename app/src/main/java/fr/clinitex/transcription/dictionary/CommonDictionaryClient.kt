package fr.clinitex.transcription.dictionary

import fr.clinitex.transcription.summaries.ClinitexSummaryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * `GET {CLINITEX_DICTIONARY_URL}` with the device token; n8n answers
 * `{version, words: [{word, replacement, matchingThreshold}]}`.
 *
 * Pure JVM so it runs under local unit tests against MockWebServer.
 */
class CommonDictionaryClient(
    private val endpointUrl: String,
    private val deviceToken: String,
    private val http: OkHttpClient = defaultHttpClient(),
) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** @param retryable true when trying again later can succeed (network down, 5xx, 408, 429). */
    class FetchException(
        message: String,
        val retryable: Boolean,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    /** True when the build was given a dictionary URL. */
    fun isConfigured(): Boolean = endpointUrl.isNotBlank()

    /** The list as sent by n8n, cleaned by [CommonWords.sanitize]; `fetchedAt` stays 0. */
    suspend fun fetch(): CommonDictionary = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw FetchException("L'URL du dictionnaire commun n'est pas configurée.", retryable = false)
        }

        val request = Request.Builder()
            .url(endpointUrl)
            .get()
            .header(ClinitexSummaryClient.TOKEN_HEADER, deviceToken)
            .build()

        val (code, body) = try {
            http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw FetchException("Dictionnaire commun injoignable : ${e.message}", retryable = true, cause = e)
        }

        if (code !in 200..299) {
            val retryable = code >= 500 || code == 408 || code == 429
            throw FetchException("Le dictionnaire commun a répondu HTTP $code", retryable = retryable)
        }

        val parsed = try {
            json.decodeFromString(CommonDictionary.serializer(), body)
        } catch (e: Exception) {
            throw FetchException("Réponse du dictionnaire commun illisible.", retryable = false, cause = e)
        }
        parsed.copy(words = CommonWords.sanitize(parsed.words), fetchedAt = 0)
    }
}
