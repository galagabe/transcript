package fr.clinitex.transcription.dictionary

import fr.clinitex.transcription.data.entity.CustomWord
import fr.clinitex.transcription.summaries.ClinitexSummaryClient
import fr.clinitex.transcription.utils.CustomWordMatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Lot 6a: merge rules, local cache and the n8n webhook contract of the common dictionary. */
class CommonDictionaryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String = "jeton-test") =
        CommonDictionaryClient(endpointUrl = server.url("/webhook/clinitex/transcription-dictionary").toString(), deviceToken = token)

    // ── merge ────────────────────────────────────────────────────────────

    @Test
    fun effectiveWords_putsOwnWordsFirstThenCommonWords() {
        val own = listOf(CustomWord(word = "Tilleuls"))
        val common = listOf(CommonWord("Pégase"), CommonWord("Clinitex"))

        val words = CommonWords.effectiveWords(own, common, disabledKeys = emptySet())

        assertEquals(listOf("Tilleuls", "Pégase", "Clinitex"), words.map { it.word })
        assertTrue(words.all { it.isEnabled })
        assertEquals("common:pégase", words[1].id)
    }

    @Test
    fun effectiveWords_ownWordWinsOverSameCommonWord() {
        val own = listOf(CustomWord(word = "pégase ", replacement = "PEGASE"))
        val common = listOf(CommonWord("Pégase", replacement = "Pégase"))

        val words = CommonWords.effectiveWords(own, common, disabledKeys = emptySet())

        assertEquals(1, words.size)
        assertEquals("PEGASE", words.single().targetWord)
    }

    @Test
    fun effectiveWords_disabledOwnWordDoesNotHideCommonWord() {
        val own = listOf(CustomWord(word = "Pégase", isEnabled = false))
        val common = listOf(CommonWord("Pégase"))

        val words = CommonWords.effectiveWords(own, common, disabledKeys = emptySet())

        assertEquals(listOf("common:pégase"), words.filter { it.isEnabled }.map { it.id })
    }

    @Test
    fun effectiveWords_leavesOutCommonWordsSwitchedOff() {
        val common = listOf(CommonWord("Pégase"), CommonWord("Clinitex"))

        val words = CommonWords.effectiveWords(emptyList(), common, disabledKeys = setOf(CommonWords.key(" PÉGASE")))

        assertEquals(listOf("Clinitex"), words.map { it.word })
    }

    @Test
    fun sanitize_dropsBlankAndDuplicateWordsAndFixesThresholds() {
        val words = CommonWords.sanitize(
            listOf(
                CommonWord("  Clinitex ", replacement = "  "),
                CommonWord("clinitex", replacement = "CLINITEX"),
                CommonWord("   "),
                CommonWord("Pégase", matchingThreshold = 0.2),
                CommonWord("Cleanbox", matchingThreshold = 1.0),
            )
        )

        assertEquals(listOf("Clinitex", "Pégase", "Cleanbox"), words.map { it.word })
        assertNull(words[0].replacement)
        assertEquals(CommonWords.DEFAULT_THRESHOLD, words[1].matchingThreshold, 0.0)
        assertEquals(1.0, words[2].matchingThreshold, 0.0)
    }

    @Test
    fun commonWords_correctTranscriptThroughExistingMatcher() {
        val words = CommonWords.effectiveWords(
            personal = emptyList(),
            common = listOf(CommonWord("clinitex", replacement = "Clinitex", matchingThreshold = 0.85)),
            disabledKeys = emptySet(),
        )

        assertEquals("Bonjour, ici Clinitex.", CustomWordMatcher.apply("Bonjour, ici clinitexe.", words))
    }

    // ── cache ────────────────────────────────────────────────────────────

    @Test
    fun cache_roundTripsAndReturnsNullWhenMissingOrCorrupt() {
        val dir = Files.createTempDirectory("dico").toFile()
        try {
            val file = File(dir, "common_dictionary.json")
            val cache = CommonDictionaryCache(file)
            assertNull(cache.read())

            val dictionary = CommonDictionary(
                version = "2-2026-10-19",
                words = listOf(CommonWord("Pégase"), CommonWord("te", replacement = "TE", matchingThreshold = 1.0)),
                fetchedAt = 1_760_000_000_000L,
            )
            cache.write(dictionary)
            assertEquals(dictionary, cache.read())
            assertFalse(File(dir, ".tmp-common_dictionary.json").exists())

            file.writeText("{pas du json")
            assertNull(cache.read())
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── webhook ──────────────────────────────────────────────────────────

    @Test
    fun fetch_sendsTokenAndParsesN8nAnswer() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"version":"2-2026-10-19T10:00:00.000Z","words":[""" +
                    """{"word":"Pégase","replacement":null,"matchingThreshold":0.9},""" +
                    """{"word":"cleanbox","replacement":"Cleanbox","matchingThreshold":0.85}]}"""
            )
        )

        val dictionary = client().fetch()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("jeton-test", request.getHeader(ClinitexSummaryClient.TOKEN_HEADER))
        assertEquals("2-2026-10-19T10:00:00.000Z", dictionary.version)
        assertEquals(listOf(CommonWord("Pégase", null, 0.9), CommonWord("cleanbox", "Cleanbox", 0.85)), dictionary.words)
        assertEquals(0L, dictionary.fetchedAt)
    }

    @Test
    fun fetch_acceptsEmptyList() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":"0-","words":[]}"""))

        assertTrue(client().fetch().words.isEmpty())
    }

    @Test
    fun fetch_refusedTokenIsNotRetried() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFetchFails(retryable = false)
    }

    @Test
    fun fetch_serverErrorIsRetried() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        assertFetchFails(retryable = true)
    }

    @Test
    fun fetch_unreadableAnswerIsNotRetried() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>proxy</html>"))
        assertFetchFails(retryable = false)
    }

    @Test
    fun fetch_withoutUrlIsNotConfigured() = runTest {
        val unconfigured = CommonDictionaryClient(endpointUrl = "", deviceToken = "jeton-test")
        assertFalse(unconfigured.isConfigured())
        try {
            unconfigured.fetch()
            fail("fetch() should fail without URL")
        } catch (e: CommonDictionaryClient.FetchException) {
            assertFalse(e.retryable)
        }
    }

    private suspend fun assertFetchFails(retryable: Boolean) {
        try {
            client().fetch()
            fail("fetch() should have failed")
        } catch (e: CommonDictionaryClient.FetchException) {
            assertEquals(retryable, e.retryable)
        }
    }
}
