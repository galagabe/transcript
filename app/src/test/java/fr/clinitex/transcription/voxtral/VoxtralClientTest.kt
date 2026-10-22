package fr.clinitex.transcription.voxtral

import fr.clinitex.transcription.summaries.ClinitexSummaryClient
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Voxtral variant: n8n webhook contract ([VoxtralClient]) and tranche files ([VoxtralTrancheStore]). */
class VoxtralClientTest {

    @get:Rule
    val tmp = TemporaryFolder()

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

    private fun client() = VoxtralClient(server.url("/webhook/clinitex/transcription-voxtral").toString(), "device-token")

    @Test
    fun `sends the tranche as multipart with Mistral's fields and reads the speakers`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"model":"voxtral-mini-transcribe-3","text":"Bonjour. Bonjour madame.","language":null,
                   "segments":[{"type":"transcription_segment","text":"Bonjour.","start":0.4,"end":1.2,"speaker_id":"speaker_1"},
                               {"type":"transcription_segment","text":"Bonjour madame.","start":1.5,"end":2.6,"speaker_id":"speaker_2"}],
                   "usage":{"prompt_audio_seconds":630}}"""
            )
        )

        val response = client().transcribe(byteArrayOf(1, 2, 3), "session-1", 2, diarize = true, contextBias = listOf("Clinitex", "autolaveuse"))

        assertEquals(listOf("speaker_1", "speaker_2"), response.segments.map { it.speakerId })
        assertEquals(1.5f, response.segments[1].start, 0f)

        val request = server.takeRequest()
        assertEquals("device-token", request.getHeader(ClinitexSummaryClient.TOKEN_HEADER))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        fun field(name: String) = Regex("name=\"$name\"\\r\\nContent-Length: \\d+\\r\\n\\r\\n([^\\r]*)").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(listOf(VoxtralClient.MODEL_ID), field("model"))
        assertEquals(listOf("fr"), field("language"))
        assertEquals(listOf("true"), field("diarize"))
        assertEquals(listOf("segment"), field("timestamp_granularities"))
        assertEquals(listOf("Clinitex", "autolaveuse"), field("context_bias"))
        assertEquals(listOf("2"), field("tranche"))
        assertTrue(body.contains("name=\"file\"; filename=\"session-1-2.flac\""))
        assertTrue(body.contains("Content-Type: audio/flac"))
    }

    @Test
    fun `server errors are retried, refusals and unreadable answers are not`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(403).setBody("jeton refusé"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>"))

        for (expectedRetryable in listOf(true, false, false)) {
            try {
                client().transcribe(byteArrayOf(0), "s", 0, diarize = false)
                fail("expected a VoxtralException")
            } catch (e: VoxtralClient.VoxtralException) {
                assertEquals(e.message, expectedRetryable, e.retryable)
            }
        }
    }

    @Test
    fun `no URL in the build is a permanent failure`() = runTest {
        val client = VoxtralClient("", "t")
        assertFalse(client.isConfigured())
        try {
            client.transcribe(byteArrayOf(0), "s", 0, diarize = false)
            fail("expected a VoxtralException")
        } catch (e: VoxtralClient.VoxtralException) {
            assertFalse(e.retryable)
        }
    }

    @Test
    fun `tranche files keep segments, failures and the final length until deleted`() {
        val store = VoxtralTrancheStore(tmp.root.resolve("voxtral/session-1"))
        assertNull(store.final())
        assertTrue(store.tranches().isEmpty())

        store.saveTranche(VoxtralTrancheStore.StoredTranche(1, listOf(VoxtralTrancheStore.StoredSegment(570f, 575f, "speaker_2", "Oui."))))
        store.saveTranche(VoxtralTrancheStore.StoredTranche(0, emptyList()))
        store.saveFinal(VoxtralTrancheStore.Final(totalSamples = 16_000L * 700, diarize = true))

        assertEquals(setOf(0, 1), store.tranches().keys)
        assertEquals("Oui.", store.tranches().getValue(1).segments.single().text)
        assertTrue(store.hasTranche(1))
        assertEquals(VoxtralTrancheStore.Final(16_000L * 700, true), store.final())
        assertNull(store.failure())

        store.saveFailure(1, "HTTP 400")
        assertEquals("HTTP 400", store.failure())

        store.delete()
        assertNull(store.final())
        assertFalse(tmp.root.resolve("voxtral/session-1").exists())
    }
}
