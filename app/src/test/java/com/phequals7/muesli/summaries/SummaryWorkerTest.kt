package com.phequals7.muesli.summaries

import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.meetings.MeetingTemplate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deferred-summary state machine ([SummaryRunner]) and the n8n webhook
 * contract ([ClinitexSummaryClient]) against a fake HTTP server.
 */
class SummaryWorkerTest {

    private lateinit var server: MockWebServer

    private class FakeSessions(
        session: RecordingSession,
        private val transcriptText: String?,
    ) : SummarySessions {
        var current: RecordingSession = session
        val states = mutableListOf<String>()

        override suspend fun session(id: String) = current.takeIf { it.id == id }
        override suspend fun transcript(sessionId: String) = transcriptText
        override suspend fun update(session: RecordingSession) {
            current = session
            states += session.summaryState
        }
    }

    private val meeting = RecordingSession(
        id = "session-1",
        kind = "meeting",
        title = "RDV Résidence Les Tilleuls",
        startedAt = 1_000_000L,
        endedAt = 1_000_000L + 2_710_000L,
        phase = "completed",
        templateId = MeetingTemplate.GENERAL.id,
        manualNotes = "Rappeler jeudi",
        summaryState = SummaryRunner.STATE_QUEUED,
    )

    private val transcript = "Speaker 1 · 0:04\nBonjour, merci de nous recevoir."

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun runner(sessions: SummarySessions, url: String = server.url("/webhook/summary").toString()) =
        SummaryRunner(
            sessions = sessions,
            client = ClinitexSummaryClient(endpointUrl = url, deviceToken = "device-token-test"),
            appVersion = "0.1.0",
        )

    @Test
    fun `sends the webhook contract and stores the returned notes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"title":"Visite Les Tilleuls","notes":"## Synthèse\nDevis à envoyer."}""")
        )
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions).run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.COMPLETED, outcome)
        assertEquals(listOf("generating", "completed"), sessions.states)
        assertEquals("## Synthèse\nDevis à envoyer.", sessions.current.summaryText)
        assertEquals("Visite Les Tilleuls", sessions.current.title)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/webhook/summary", recorded.path)
        assertEquals("device-token-test", recorded.getHeader(ClinitexSummaryClient.TOKEN_HEADER))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("session-1", body["sessionId"]!!.jsonPrimitive.content)
        assertEquals("RDV Résidence Les Tilleuls", body["title"]!!.jsonPrimitive.content)
        assertEquals("general", body["templateId"]!!.jsonPrimitive.content)
        assertEquals(MeetingTemplate.GENERAL.instructions, body["templateInstructions"]!!.jsonPrimitive.content)
        assertEquals("Rappeler jeudi", body["manualNotes"]!!.jsonPrimitive.content)
        assertEquals(transcript, body["transcript"]!!.jsonPrimitive.content)
        assertEquals("2710", body["durationSec"]!!.jsonPrimitive.content)
        assertEquals("0.1.0", body["appVersion"]!!.jsonPrimitive.content)
        assertTrue(body["speakerNames"]!!.jsonObject.isEmpty())
        assertEquals(JsonNull, body["destination"])
    }

    @Test
    fun `voice note is cleaned up with the noteVocale template`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"title":"Consignes équipe du soir","notes":"## Note\nTexte.\n\n## À faire\n- [ ] Fermer"}""")
        )
        val voiceNote = meeting.copy(
            id = "vocal-1",
            kind = RecordingSession.KIND_VOICE_NOTE,
            title = "Vocal du 16 oct. à 17:42",
            templateId = MeetingTemplate.NOTE_VOCALE.id,
            manualNotes = "",
        )
        val sessions = FakeSessions(voiceNote, "alors pour l'équipe du soir euh fermer les fenêtres")

        val outcome = runner(sessions).run(voiceNote.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.COMPLETED, outcome)
        assertEquals("Consignes équipe du soir", sessions.current.title)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("noteVocale", body["templateId"]!!.jsonPrimitive.content)
        assertEquals(MeetingTemplate.NOTE_VOCALE.instructions, body["templateInstructions"]!!.jsonPrimitive.content)
    }

    @Test
    fun `keeps the meeting title when the webhook returns none`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"notes":"Notes"}"""))
        val sessions = FakeSessions(meeting, transcript)

        runner(sessions).run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals("RDV Résidence Les Tilleuls", sessions.current.title)
    }

    @Test
    fun `server error goes back to queued and asks for a retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions).run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.RETRY, outcome)
        assertEquals(listOf("generating", "queued"), sessions.states)
        assertEquals("", sessions.current.summaryText)
    }

    @Test
    fun `unreachable server asks for a retry`() = runTest {
        val url = server.url("/webhook/summary").toString()
        server.shutdown()
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions, url).run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.RETRY, outcome)
        assertEquals("queued", sessions.current.summaryState)
    }

    @Test
    fun `last retryable attempt fails and keeps the raw transcript`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions).run(meeting.id, attempt = 7, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.FAILED, outcome)
        assertEquals("failed", sessions.current.summaryState)
        assertTrue(sessions.current.summaryText.contains(transcript))
        assertTrue(sessions.current.summaryText.contains("Rappeler jeudi"))
    }

    @Test
    fun `rejected token fails without retrying`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad token"))
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions).run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.FAILED, outcome)
        assertEquals("failed", sessions.current.summaryState)
        assertTrue(sessions.current.summaryText.contains("HTTP 401"))
    }

    @Test
    fun `empty notes count as a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"title":"T","notes":"  "}"""))
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions).run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.FAILED, outcome)
        assertEquals("failed", sessions.current.summaryState)
    }

    @Test
    fun `blank transcript is marked unavailable without calling the webhook`() = runTest {
        val sessions = FakeSessions(meeting, "   ")

        val outcome = runner(sessions).run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.SKIPPED, outcome)
        assertEquals("unavailable", sessions.current.summaryState)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing webhook url is marked unavailable`() = runTest {
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions, url = "").run(meeting.id, attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.SKIPPED, outcome)
        assertEquals("unavailable", sessions.current.summaryState)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `deleted session is skipped`() = runTest {
        val sessions = FakeSessions(meeting, transcript)

        val outcome = runner(sessions).run("other-session", attempt = 0, maxAttempts = 8)

        assertEquals(SummaryRunner.Outcome.SKIPPED, outcome)
        assertTrue(sessions.states.isEmpty())
    }
}
