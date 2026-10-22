package fr.clinitex.transcription.utils

import fr.clinitex.transcription.data.SharedStore

/**
 * Filler-word removal + custom dictionary (own words and common Clinitex
 * words, lot 6a), applied to every transcribed chunk whatever the engine:
 * Parakeet on the phone or Voxtral online.
 */
object TranscriptPostProcessor {

    /** @param fuzzyDictionary false for Voxtral: exact dictionary words only (Mistral already gets the list). */
    suspend fun apply(store: SharedStore, text: String, fuzzyDictionary: Boolean = true): String {
        if (text.isBlank()) return text
        var processed = text
        if (store.isFillerWordRemovalEnabled) {
            processed = FillerWordFilter.apply(processed)
        }
        if (store.isCustomDictionaryEnabled) {
            processed = CustomWordMatcher.apply(processed, store.getDictionaryWords(), fuzzy = fuzzyDictionary)
        }
        return processed.trim()
    }
}
