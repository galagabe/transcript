package fr.clinitex.transcription.utils

import fr.clinitex.transcription.data.entity.CustomWord
import org.junit.Assert.assertEquals
import org.junit.Test

class TextProcessingTest {

    @Test
    fun testFillerWordFilter() {
        val rawInput = "Uh hello, um, this is, you know, a simple test."
        val expectedOutput = "Hello, this is, a simple test."
        val processed = FillerWordFilter.apply(rawInput)
        assertEquals(expectedOutput, processed)
    }

    @Test
    fun testFrenchFillerWords() {
        val rawInput = "Euh donc on passe, euh, à trois passages, heu par semaine."
        val expectedOutput = "Donc on passe, à trois passages, par semaine."
        assertEquals(expectedOutput, FillerWordFilter.apply(rawInput))
    }

    @Test
    fun testFillerWordFilterCapitalization() {
        val rawInput = "um test"
        val expectedOutput = "Test"
        val processed = FillerWordFilter.apply(rawInput)
        assertEquals(expectedOutput, processed)
    }

    @Test
    fun testJaroWinklerSimilarityExact() {
        val s1 = "muesli"
        val s2 = "muesli"
        val sim = CustomWordMatcher.jaroWinklerSimilarity(s1, s2)
        assertEquals(1.0, sim, 0.001)
    }

    @Test
    fun testCustomWordMatcherReplacements() {
        val customWords = listOf(
            CustomWord(word = "muesli", replacement = "MuesliApp", matchingThreshold = 0.85),
            CustomWord(word = "chatgpt", replacement = "ChatGPT", matchingThreshold = 0.8)
        )

        // Exact match replacement
        val input1 = "I love muesli app."
        val expected1 = "I love MuesliApp app."
        assertEquals(expected1, CustomWordMatcher.apply(input1, customWords))

        // Fuzzy match replacement: "musli" is highly similar to "muesli"
        val input2 = "I love musli app."
        val expected2 = "I love MuesliApp app."
        assertEquals(expected2, CustomWordMatcher.apply(input2, customWords))
        // Exact words only (Voxtral): the near miss is left alone.
        assertEquals(input2, CustomWordMatcher.apply(input2, customWords, fuzzy = false))
        assertEquals(expected1, CustomWordMatcher.apply(input1, customWords, fuzzy = false))
    }

    @Test
    fun `short dictionary words are only replaced when heard exactly`() {
        // Common Clinitex list (2026-10-19): ESAT and Onet at 0.9, Mapa at 0.9, sigles at 1.
        val words = listOf(
            CustomWord(word = "ESAT", matchingThreshold = 0.9),
            CustomWord(word = "Onet", matchingThreshold = 0.9),
            CustomWord(word = "Mapa", matchingThreshold = 0.9),
            CustomWord(word = "autolaveuse", matchingThreshold = 0.85),
            CustomWord(word = "mono brosse", replacement = "monobrosse", matchingThreshold = 0.85),
        )
        // "est" was ESAT (0.93) and "ont" was Onet (0.93).
        assertEquals(
            "On est sûr qu'ils ont la papa et la mappa.",
            CustomWordMatcher.apply("On est sûr qu'ils ont la papa et la mappa.", words),
        )
        // Exact short words still get their spelling.
        assertEquals("Travail avec un ESAT et Onet.", CustomWordMatcher.apply("Travail avec un esat et onet.", words))
        // Long words keep the approximate correction, plural included.
        assertEquals("deux autolaveuse", CustomWordMatcher.apply("deux autolaveuses", words))
        assertEquals("une monobrosse", CustomWordMatcher.apply("une mono brosse", words))
        // Much shorter or longer words are not "near" a long one.
        assertEquals("une auto", CustomWordMatcher.apply("une auto", words))
    }
}
