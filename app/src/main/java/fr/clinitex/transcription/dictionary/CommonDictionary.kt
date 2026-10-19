package fr.clinitex.transcription.dictionary

import fr.clinitex.transcription.data.entity.CustomWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/**
 * Lot 6a: Clinitex word list shared by every employee, kept in the n8n data
 * table "Clindy - dictionnaire commun" and downloaded by [CommonDictionaryWorker].
 * It adds to each user's own words (custom_words), which win on a duplicate.
 */
@Serializable
data class CommonDictionary(
    val version: String = "",
    val words: List<CommonWord> = emptyList(),
    /** Set by the app when the list is received; 0 in the webhook answer. */
    val fetchedAt: Long = 0,
)

/** Same fields as [CustomWord], so [fr.clinitex.transcription.utils.CustomWordMatcher] applies them unchanged. */
@Serializable
data class CommonWord(
    val word: String,
    val replacement: String? = null,
    val matchingThreshold: Double = CommonWords.DEFAULT_THRESHOLD,
)

object CommonWords {
    const val DEFAULT_THRESHOLD = 0.85
    private const val ID_PREFIX = "common:"

    /** Duplicate / disable key: "Pégase " and "pégase" are the same word. */
    fun key(word: String): String = word.trim().lowercase(Locale.ROOT)

    /** Drops blank words and duplicates (first kept), trims, keeps thresholds in the app's 0.5–1 range. */
    fun sanitize(words: List<CommonWord>): List<CommonWord> {
        val seen = HashSet<String>()
        return words.mapNotNull { w ->
            val word = w.word.trim()
            if (word.isEmpty() || !seen.add(key(word))) return@mapNotNull null
            CommonWord(
                word = word,
                replacement = w.replacement?.trim()?.ifEmpty { null },
                matchingThreshold = w.matchingThreshold.takeIf { it in 0.5..1.0 } ?: DEFAULT_THRESHOLD,
            )
        }
    }

    /** Keys of the common words a user's own enabled word replaces. */
    fun overriddenKeys(personal: List<CustomWord>): Set<String> =
        personal.filter { it.isEnabled }.map { key(it.word) }.toSet()

    /**
     * Words applied to a transcript: the user's own words first, then the
     * common words the user neither disabled nor replaced by an own word.
     */
    fun effectiveWords(
        personal: List<CustomWord>,
        common: List<CommonWord>,
        disabledKeys: Set<String>,
    ): List<CustomWord> {
        val overridden = overriddenKeys(personal)
        return personal + common
            .filter { key(it.word) !in disabledKeys && key(it.word) !in overridden }
            .map {
                CustomWord(
                    id = ID_PREFIX + key(it.word),
                    word = it.word,
                    replacement = it.replacement,
                    matchingThreshold = it.matchingThreshold,
                    createdAt = 0,
                    isEnabled = true,
                )
            }
    }
}

/** Last received list, as a JSON file; written atomically so a crash never leaves half a list. */
class CommonDictionaryCache(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Null when nothing was received yet or the file is unreadable. */
    fun read(): CommonDictionary? = try {
        if (file.exists()) json.decodeFromString(CommonDictionary.serializer(), file.readText()) else null
    } catch (e: Exception) {
        null
    }

    fun write(dictionary: CommonDictionary) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".tmp-${file.name}")
        tmp.writeText(json.encodeToString(CommonDictionary.serializer(), dictionary))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("Impossible d'enregistrer le dictionnaire commun.")
            }
        }
    }
}
