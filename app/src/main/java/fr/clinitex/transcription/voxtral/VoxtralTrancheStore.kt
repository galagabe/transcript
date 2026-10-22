package fr.clinitex.transcription.voxtral

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Per-session progress of the Voxtral variant, as small JSON files in
 * `files/voxtral/<sessionId>/` (no Room schema change): one file per
 * transcribed tranche, one per tranche that failed for good, and
 * `final.json` once the recording has stopped (its length). The transcript
 * is assembled when `final.json` and every planned tranche are there; the
 * folder is then deleted.
 */
class VoxtralTrancheStore(private val dir: File) {

    @Serializable
    data class StoredSegment(val start: Float, val end: Float, val speakerId: String? = null, val text: String)

    /** Segments in seconds from the start of the recording. */
    @Serializable
    data class StoredTranche(val index: Int, val segments: List<StoredSegment>)

    @Serializable
    data class Final(val totalSamples: Long, val diarize: Boolean)

    private val json = Json { ignoreUnknownKeys = true }

    fun saveTranche(tranche: StoredTranche) = write("tranche-${tranche.index}.json", json.encodeToString(StoredTranche.serializer(), tranche))

    fun tranches(): Map<Int, StoredTranche> = dir.listFiles { f -> f.name.startsWith("tranche-") && f.name.endsWith(".json") }
        .orEmpty()
        .mapNotNull { runCatching { json.decodeFromString(StoredTranche.serializer(), it.readText()) }.getOrNull() }
        .associateBy { it.index }

    fun hasTranche(index: Int): Boolean = File(dir, "tranche-$index.json").exists()

    /** A tranche that cannot be transcribed (4xx, unreadable answer): the session fails. */
    fun saveFailure(index: Int, message: String) = write("failed-$index.txt", message)

    fun failure(): String? = dir.listFiles { f -> f.name.startsWith("failed-") }.orEmpty()
        .minByOrNull { it.name }?.readText()

    fun saveFinal(final: Final) = write("final.json", json.encodeToString(Final.serializer(), final))

    fun final(): Final? = File(dir, "final.json").takeIf { it.exists() }
        ?.let { runCatching { json.decodeFromString(Final.serializer(), it.readText()) }.getOrNull() }

    fun delete() {
        dir.deleteRecursively()
    }

    /** Write then rename, so a reader never sees half a file. */
    private fun write(name: String, text: String) {
        dir.mkdirs()
        val tmp = File(dir, ".$name.tmp")
        tmp.writeText(text)
        val target = File(dir, name)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}
