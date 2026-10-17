package com.phequals7.muesli.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.phequals7.muesli.data.dao.CustomWordDao
import com.phequals7.muesli.data.dao.DictationResultDao
import com.phequals7.muesli.data.dao.RecordingSessionDao
import com.phequals7.muesli.data.dao.SpeakerDao
import com.phequals7.muesli.data.dao.TranscriptDao
import com.phequals7.muesli.data.entity.CustomWord
import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.data.entity.SpeakerName
import com.phequals7.muesli.data.entity.Transcript
import com.phequals7.muesli.data.entity.TranscriptSegment

@Database(
    entities = [
        CustomWord::class,
        DictationResult::class,
        RecordingSession::class,
        Transcript::class,
        TranscriptSegment::class,
        SpeakerName::class,
    ],
    version = 5,
    // Schemas land in app/schemas (exact CREATE statements for migrations).
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customWordDao(): CustomWordDao
    abstract fun dictationResultDao(): DictationResultDao
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun speakerDao(): SpeakerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds DictationResult.durationMs (keeps existing rows). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dictation_results ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds RecordingSession.templateId + manualNotes for meeting notes. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN templateId TEXT NOT NULL DEFAULT 'general'")
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN manualNotes TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds RecordingSession.summaryText + summaryState for AI summaries. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN summaryText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN summaryState TEXT NOT NULL DEFAULT 'notStarted'")
            }
        }

        /** Lot 3: diarized transcript segments + user-given speaker names (new tables only). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Same SQL as Room's generated schema (app/schemas/.../5.json).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transcript_segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, `startSec` REAL NOT NULL, `endSec` REAL NOT NULL, " +
                        "`speaker` INTEGER NOT NULL, `text` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transcript_segments_sessionId` ON `transcript_segments` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `speaker_names` (`sessionId` TEXT NOT NULL, `speaker` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, PRIMARY KEY(`sessionId`, `speaker`))"
                )
            }
        }

        /** Exposed for migration tests. */
        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "muesli_database"
                )
                // No destructive fallback: meetings on the phone are real data,
                // every schema change ships a migration.
                .addMigrations(*MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
