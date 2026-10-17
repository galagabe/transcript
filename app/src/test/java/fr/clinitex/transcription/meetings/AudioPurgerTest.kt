package fr.clinitex.transcription.meetings

import fr.clinitex.transcription.data.entity.RecordingSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Lot 5: 7-day audio retention ([AudioPurger]) on a real temp directory. */
class AudioPurgerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)
    private lateinit var dir: File

    private class FakeSessions(var rows: List<RecordingSession>) : AudioPurgeSessions {
        val deleted = mutableMapOf<String, Long>()
        override suspend fun all() = rows
        override suspend fun markAudioDeleted(sessionId: String, deletedAt: Long) {
            deleted[sessionId] = deletedAt
            rows = rows.map { if (it.id == sessionId) it.copy(audioFileName = null, audioDeletedAt = deletedAt) else it }
        }
    }

    @Before
    fun setUp() {
        dir = tmp.newFolder("meetings")
    }

    private fun wav(name: String, modifiedAt: Long = now): File =
        File(dir, name).apply {
            writeBytes(ByteArray(44))
            setLastModified(modifiedAt)
        }

    private fun session(id: String, endedAt: Long?, startedAt: Long? = endedAt?.minus(hour), audio: String? = "$id.wav") =
        RecordingSession(
            id = id,
            kind = RecordingSession.KIND_MEETING,
            title = id,
            startedAt = startedAt,
            endedAt = endedAt,
            phase = "completed",
            audioFileName = audio,
        )

    private suspend fun purge(fake: FakeSessions) = AudioPurger(fake, dir, now = { now }).purge()

    @Test
    fun deletesAudioOlderThanSevenDaysAfterTheEndOfRecording() = runTest {
        val old = wav("old.wav", modifiedAt = now - 8 * day)
        val fake = FakeSessions(listOf(session("old", endedAt = now - 7 * day - 1)))

        val report = purge(fake)

        assertFalse(old.exists())
        assertEquals(mapOf("old" to now), fake.deleted)
        assertNull(fake.rows.single().audioFileName)
        assertEquals(now, fake.rows.single().audioDeletedAt)
        assertEquals(AudioPurger.Report(sessionAudio = 1, tempFiles = 0, orphanFiles = 0), report)
    }

    @Test
    fun keepsAudioYoungerThanSevenDays() = runTest {
        val recent = wav("recent.wav")
        // Started 8 days ago but ended 6 days ago: retention counts from the end.
        val fake = FakeSessions(listOf(session("recent", endedAt = now - 6 * day, startedAt = now - 8 * day)))

        val report = purge(fake)

        assertTrue(recent.exists())
        assertTrue(fake.deleted.isEmpty())
        assertEquals(0, report.total)
    }

    @Test
    fun clearsTheRowEvenWhenTheFileIsAlreadyGone() = runTest {
        val fake = FakeSessions(listOf(session("gone", endedAt = now - 10 * day)))

        purge(fake)

        assertEquals(mapOf("gone" to now), fake.deleted)
    }

    @Test
    fun ignoresSessionsWithoutAudioOrAlreadyPurged() = runTest {
        val fake = FakeSessions(
            listOf(
                session("textOnly", endedAt = now - 30 * day, audio = null),
                session("purged", endedAt = now - 30 * day, audio = null).copy(audioDeletedAt = now - 20 * day),
            )
        )

        purge(fake)

        assertTrue(fake.deleted.isEmpty())
    }

    @Test
    fun usesTheStartWhenTheEndIsMissing() = runTest {
        wav("noEnd.wav", modifiedAt = now - 9 * day)
        val fake = FakeSessions(listOf(session("noEnd", endedAt = null, startedAt = now - 9 * day)))

        purge(fake)

        assertEquals(setOf("noEnd"), fake.deleted.keys)
    }

    @Test
    fun deletesOrphanTempWavAfter24Hours() = runTest {
        val stale = wav(".tmp-abc.wav", modifiedAt = now - 25 * hour)
        val fresh = wav(".tmp-def.wav", modifiedAt = now - 23 * hour)

        val report = purge(FakeSessions(emptyList()))

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertEquals(1, report.tempFiles)
    }

    @Test
    fun deletesUnreferencedWavOnlyAfterTheRetentionPeriod() = runTest {
        // Left behind by a capture interrupted before the session was completed.
        val crashed = wav("crashed.wav", modifiedAt = now - 8 * day)
        val inProgress = wav("live.wav", modifiedAt = now - 1000)
        val notAudio = File(dir, "notes.txt").apply { writeText("x"); setLastModified(now - 30 * day) }

        val report = purge(FakeSessions(emptyList()))

        assertFalse(crashed.exists())
        assertTrue(inProgress.exists())
        assertTrue(notAudio.exists())
        assertEquals(1, report.orphanFiles)
    }

    @Test
    fun keepsReferencedWavWhateverItsFileDate() = runTest {
        // Old file date but recording ended 2 days ago (e.g. restored or copied file).
        val kept = wav("kept.wav", modifiedAt = now - 30 * day)

        purge(FakeSessions(listOf(session("kept", endedAt = now - 2 * day))))

        assertTrue(kept.exists())
    }

    @Test
    fun deletionDueAtIsSevenDaysAfterTheEnd() {
        assertEquals(now + 7 * day, AudioPurgeWorker.deletionDueAt(session("s", endedAt = now)))
        assertNull(AudioPurgeWorker.deletionDueAt(session("s", endedAt = null, startedAt = null)))
    }
}
