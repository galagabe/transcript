package fr.clinitex.transcription.summaries

import fr.clinitex.transcription.data.entity.RecordingSession
import fr.clinitex.transcription.summaries.SummaryRecovery.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 6b: which summaries the 30-min check and "Forcer la reconnexion" relaunch. */
class SummaryRecoveryTest {

    private fun session(id: String, summaryState: String, phase: String = "completed") = RecordingSession(
        id = id,
        kind = RecordingSession.KIND_MEETING,
        title = id,
        phase = phase,
        summaryState = summaryState,
    )

    private fun select(
        sessions: List<RecordingSession>,
        mode: Mode,
        work: Map<String, SummaryWorkState> = emptyMap(),
        failures: Map<String, SummaryFailureInfo> = emptyMap(),
    ): List<String> = SummaryRecovery.select(
        sessions = sessions,
        workState = { work[it] ?: SummaryWorkState.NONE },
        failure = { failures[it] ?: SummaryFailureInfo() },
        mode = mode,
    ).map { it.id }

    @Test
    fun relaunchesWaitingAndStuckSummariesWithNoWorkBehindThem() {
        // "generating" with no work: the app was killed during the call.
        val rows = listOf(session("queued", "queued"), session("stuck", "generating"))

        assertEquals(listOf("queued", "stuck"), select(rows, Mode.AUTO))
        assertEquals(listOf("queued", "stuck"), select(rows, Mode.FORCE))
    }

    @Test
    fun runningWorkIsNeverInterrupted() {
        val rows = listOf(session("a", "generating"), session("b", "queued"))
        val work = mapOf("a" to SummaryWorkState.RUNNING, "b" to SummaryWorkState.RUNNING)

        assertTrue(select(rows, Mode.AUTO, work).isEmpty())
        assertTrue(select(rows, Mode.FORCE, work).isEmpty())
    }

    @Test
    fun waitingWorkIsKeptByTheCheckButSentNowWhenForced() {
        val rows = listOf(session("a", "queued"))
        val work = mapOf("a" to SummaryWorkState.WAITING)

        assertTrue(select(rows, Mode.AUTO, work).isEmpty())
        assertEquals(listOf("a"), select(rows, Mode.FORCE, work))
    }

    @Test
    fun networkFailureIsRetriedAutomaticallyAtMostThreeTimes() {
        val rows = listOf(session("a", "failed"))
        fun auto(retries: Int) = select(rows, Mode.AUTO, failures = mapOf("a" to SummaryFailureInfo(autoRetries = retries)))

        assertEquals(listOf("a"), auto(0))
        assertEquals(listOf("a"), auto(SummaryRecovery.MAX_AUTO_RETRIES - 1))
        assertTrue(auto(SummaryRecovery.MAX_AUTO_RETRIES).isEmpty())
    }

    @Test
    fun permanentFailureWaitsForTheUser() {
        val rows = listOf(session("a", "failed"))
        val failures = mapOf("a" to SummaryFailureInfo(permanent = true))

        assertTrue(select(rows, Mode.AUTO, failures = failures).isEmpty())
        assertEquals(listOf("a"), select(rows, Mode.FORCE, failures = failures))
    }

    @Test
    fun forceRelaunchesFailuresWhateverTheirRetryCount() {
        val rows = listOf(session("a", "failed"))
        val failures = mapOf("a" to SummaryFailureInfo(autoRetries = 10))

        assertEquals(listOf("a"), select(rows, Mode.FORCE, failures = failures))
    }

    @Test
    fun ignoresDoneUnavailableAndUntranscribedSessions() {
        val rows = listOf(
            session("done", "completed"),
            session("none", "notStarted"),
            session("empty", "unavailable"),
            session("recording", "queued", phase = "recording"),
            session("transcribing", "queued", phase = "transcribing"),
            session("captureFailed", "failed", phase = "failed"),
        )

        assertTrue(select(rows, Mode.AUTO).isEmpty())
        assertTrue(select(rows, Mode.FORCE).isEmpty())
    }
}
