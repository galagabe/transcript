package com.phequals7.muesli.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phequals7.muesli.data.entity.SpeakerName
import com.phequals7.muesli.data.entity.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration scaffold: hand-builds a v1-shaped database (as Room v1 wrote
 * it), runs all migrations to the current version via the real
 * [AppDatabase] open path, and asserts data survives with new columns
 * defaulted. Extend with per-version fixtures as the schema evolves.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV1Database() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { db ->
            db.execSQL(
                """CREATE TABLE dictation_results (
                     id TEXT NOT NULL PRIMARY KEY,
                     requestID TEXT NOT NULL,
                     sessionID TEXT,
                     text TEXT NOT NULL,
                     createdAt INTEGER NOT NULL,
                     engineIdentifier TEXT NOT NULL
                   )"""
            )
            db.execSQL(
                """CREATE TABLE recording_sessions (
                     id TEXT NOT NULL PRIMARY KEY,
                     requestID TEXT,
                     kind TEXT NOT NULL,
                     title TEXT NOT NULL,
                     createdAt INTEGER NOT NULL,
                     startedAt INTEGER,
                     endedAt INTEGER,
                     phase TEXT NOT NULL,
                     audioFileName TEXT,
                     transcriptID TEXT,
                     engineIdentifier TEXT,
                     errorMessage TEXT
                   )"""
            )
            db.execSQL(
                """CREATE TABLE transcripts (
                     id TEXT NOT NULL PRIMARY KEY,
                     sessionID TEXT NOT NULL,
                     text TEXT NOT NULL,
                     createdAt INTEGER NOT NULL,
                     engineIdentifier TEXT NOT NULL
                   )"""
            )
            db.execSQL(
                """CREATE TABLE custom_words (
                     id TEXT NOT NULL PRIMARY KEY,
                     word TEXT NOT NULL,
                     replacement TEXT,
                     matchingThreshold REAL NOT NULL,
                     createdAt INTEGER NOT NULL,
                     isEnabled INTEGER NOT NULL
                   )"""
            )
            db.execSQL("INSERT INTO dictation_results VALUES ('d1','r1',NULL,'hello from v1',1700000000000,'parakeet')")
            db.execSQL("INSERT INTO recording_sessions VALUES ('s1',NULL,'meeting','v1 meeting',1700000000000,NULL,NULL,'completed',NULL,NULL,'parakeet',NULL)")
            // Without this Room sees user_version=0, treats the DB as fresh,
            // and fails schema validation instead of running migrations.
            db.version = 1
        }
    }

    @Test
    fun migrate1ToLatest_preservesDataAndAddsColumns() {
        createV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

        // Opening the DB forces the migration chain v1 -> latest.
        val results = runBlocking { db.dictationResultDao().getAllResults() }
        assertEquals(1, results.size)
        assertEquals("hello from v1", results[0].text)
        assertEquals(0L, results[0].durationMs) // MIGRATION_1_2 default

        val session = runBlocking { db.recordingSessionDao().getSessionById("s1") }
        assertNotNull(session)
        assertEquals("v1 meeting", session!!.title)
        assertEquals("general", session.templateId) // MIGRATION_2_3 default
        assertEquals("", session.manualNotes)
        assertEquals("", session.summaryText) // MIGRATION_3_4 default
        assertEquals("notStarted", session.summaryState)

        // MIGRATION_4_5: speaker tables exist, empty, and are usable.
        val speakers = db.speakerDao()
        runBlocking {
            assertEquals(emptyList<TranscriptSegment>(), speakers.getSegments("s1"))
            assertEquals(emptyList<SpeakerName>(), speakers.getSpeakerNames("s1"))
            speakers.replaceSegments("s1", listOf(TranscriptSegment(sessionId = "s1", startSec = 0f, endSec = 4f, speaker = 1, text = "Bonjour")))
            speakers.replaceSpeakerNames("s1", listOf(SpeakerName("s1", 1, "Commercial Clinitex"), SpeakerName("s1", 2, " ")))
            assertEquals("Bonjour", speakers.getSegments("s1").single().text)
            assertEquals(listOf(SpeakerName("s1", 1, "Commercial Clinitex")), speakers.getSpeakerNames("s1"))
        }

        db.close()
    }

    /** v4 database as shipped before lot 3 (all session columns), opened at the latest version. */
    @Test
    fun migrate4To5_keepsMeetingsAndAddsSpeakerTables() {
        createV1Database()
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE dictation_results ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN templateId TEXT NOT NULL DEFAULT 'general'")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN manualNotes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN summaryText TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN summaryState TEXT NOT NULL DEFAULT 'notStarted'")
            db.execSQL("UPDATE recording_sessions SET templateId = 'rdvCommercial', summaryText = '## Synthèse', summaryState = 'completed' WHERE id = 's1'")
            db.execSQL("INSERT INTO transcripts VALUES ('t1','s1','Intervenant 1 · 0:04\nBonjour',1700000000000,'parakeet')")
            db.version = 4
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

        val session = runBlocking { db.recordingSessionDao().getSessionById("s1") }
        assertEquals("rdvCommercial", session!!.templateId)
        assertEquals("## Synthèse", session.summaryText)
        assertEquals("completed", session.summaryState)
        assertEquals("Intervenant 1 · 0:04\nBonjour", runBlocking { db.transcriptDao().getTranscriptForSession("s1") }!!.text)
        assertEquals(emptyList<TranscriptSegment>(), runBlocking { db.speakerDao().getSegments("s1") })

        db.close()
    }
}
