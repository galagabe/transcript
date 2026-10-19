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

/** 48 h audio retention (lot 6c; 7 days in lot 5) ([AudioPurger]) on a real temp directory. */
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
    fun deletesAudioOlder48HoursAfterTheEndOfRecording() = runTest {
        val old = wav("old.wav", modifiedAt = now - 3 * day)
        val fake = FakeSessions(listOf(session("old", endedAt = now - 48 * hour - 1)))

        val report = purge(fake)

        assertFalse(old.exists())
        assertEquals(mapOf("old" to now), fake.deleted)
        assertNull(fake.rows.single().audioFileName)
        assertEquals(now, fake.rows.single().audioDeletedAt)
        assertEquals(AudioPurger.Report(sessionAudio = 1, tempFiles = 0, orphanFiles = 0), report)
    }

    @Test
    fun keepsAudioYoungerThan48Hours() = runTest {
        val recent = wav("recent.wav")
        // Started 50 h ago but ended 47 h ago: retention counts from the end.
        val fake = FakeSessions(listOf(session("recent", endedAt = now - 47 * hour, startedAt = now - 50 * hour)))

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
        val crashed = wav("crashed.wav", modifiedAt = now - 49 * hour)
        val recentCrash = wav("recentCrash.wav", modifiedAt = now - 47 * hour)
        val inProgress = wav("live.wav", modifiedAt = now - 1000)
        val notAudio = File(dir, "notes.txt").apply { writeText("x"); setLastModified(now - 30 * day) }

        val report = purge(FakeSessions(emptyList()))

        assertFalse(crashed.exists())
        assertTrue(recentCrash.exists())
        assertTrue(inProgress.exists())
        assertTrue(notAudio.exists())
        assertEquals(1, report.orphanFiles)
    }

    @Test
    fun keepsReferencedWavWhateverItsFileDate() = runTest {
        // Old file date but recording ended 1 day ago (e.g. restored or copied file).
        val kept = wav("kept.wav", modifiedAt = now - 30 * day)

        purge(FakeSessions(listOf(session("kept", endedAt = now - day))))

        assertTrue(kept.exists())
    }

    @Test
    fun deletionDueAtIs48HoursAfterTheEnd() {
        assertEquals(now + 48 * hour, AudioPurgeWorker.deletionDueAt(session("s", endedAt = now)))
        assertNull(AudioPurgeWorker.deletionDueAt(session("s", endedAt = null, startedAt = null)))
    }
}
