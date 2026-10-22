package fr.clinitex.transcription.voxtral

import fr.clinitex.transcription.voxtral.VoxtralTranches.TimedSegment
import fr.clinitex.transcription.voxtral.VoxtralTranches.TrancheResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voxtral variant: 10-minute tranches, their 30 s overlaps and speakers across tranches. */
class VoxtralTranchesTest {

    private val rate = 16_000

    @Test
    fun `while recording only full tranches leave, each with the previous 30 s`() {
        val tranches = VoxtralTranches.plan(1250L * rate, final = false)
        assertEquals(listOf(0, 1), tranches.map { it.index })
        assertEquals(0f, tranches[0].startSec, 0f)
        assertEquals(600f, tranches[0].endSec, 0f)
        assertEquals(570f, tranches[1].startSec, 0f) // 30 s of the previous tranche
        assertEquals(1200f, tranches[1].endSec, 0f)
        assertEquals(600L * rate, tranches[1].newStartSample)
    }

    @Test
    fun `at stop the last tranche is shorter and earlier ones are unchanged`() {
        val running = VoxtralTranches.plan(1250L * rate, final = false)
        val final = VoxtralTranches.plan(1250L * rate, final = true)
        assertEquals(3, final.size)
        assertEquals(running, final.take(2))
        assertEquals(1170f, final[2].startSec, 0f)
        assertEquals(1250f, final[2].endSec, 0f)
    }

    @Test
    fun `a tail under one second is not sent and nothing leaves before 10 minutes`() {
        assertEquals(2, VoxtralTranches.plan(1200L * rate + rate / 2, final = true).size)
        assertTrue(VoxtralTranches.plan(300L * rate, final = false).isEmpty())
        assertEquals(1, VoxtralTranches.plan(300L * rate, final = true).size)
        assertTrue(VoxtralTranches.plan(0, final = true).isEmpty())
    }

    private fun seg(start: Float, end: Float, speaker: String?, text: String) = TimedSegment(start, end, speaker, text)

    // Tranches of 100 s with 20 s overlap: [0, 100) and [80, 180).
    private val small = VoxtralTranches.plan(180L * rate, final = true, trancheSec = 100, overlapSec = 20)

    @Test
    fun `cut is the common pause closest to the middle of the overlap`() {
        val previous = listOf(seg(70f, 84f, null, "a"), seg(85f, 92f, null, "b"), seg(93f, 100f, null, "c coupé"))
        val next = listOf(seg(80f, 84.1f, null, "a coupé"), seg(85.1f, 92.2f, null, "b"), seg(93.1f, 99f, null, "c"))
        assertEquals(92f, VoxtralTranches.cutPoint(previous, next, 80f, 100f), 0f)
    }

    @Test
    fun `without a common pause both cut versions are kept rather than losing words`() {
        val results = listOf(
            TrancheResult(small[0], listOf(seg(0f, 60f, null, "Début."), seg(61f, 100f, null, "Une très longue phrase coup"))),
            TrancheResult(small[1], listOf(seg(80f, 120f, null, "ngue phrase sans pause."), seg(121f, 170f, null, "Fin."))),
        )
        assertEquals(90f, VoxtralTranches.cutPoint(results[0].segments, results[1].segments, 80f, 100f), 0f)
        assertEquals(
            listOf("Début.", "Une très longue phrase coup", "ngue phrase sans pause.", "Fin."),
            VoxtralTranches.assemble(results).map { it.text },
        )
    }

    @Test
    fun `overlap is written once and speakers are linked by what both tranches heard`() {
        val results = listOf(
            TrancheResult(
                small[0],
                listOf(
                    seg(0f, 10f, "speaker_1", "Bonjour à tous."),
                    seg(11f, 40f, "speaker_2", "Merci de nous recevoir."),
                    seg(41f, 85f, "speaker_1", "Nous allons parler du contrat."),
                    seg(86f, 92f, "speaker_2", "Très bien."),
                    seg(93f, 100f, "speaker_1", "Alors le premier po"),
                ),
            ),
            // Voxtral numbers this tranche's voices on its own: speaker_2 is the first voice here.
            TrancheResult(
                small[1],
                listOf(
                    seg(80f, 85f, "speaker_2", "parler du contrat."),
                    seg(86f, 92f, "speaker_1", "Très bien."),
                    seg(93f, 101f, "speaker_2", "Alors le premier point."),
                    seg(102f, 150f, "speaker_1", "D'accord pour moi."),
                    seg(151f, 180f, "speaker_2", "Parfait."),
                ),
            ),
        )

        val labeled = VoxtralTranches.assemble(results)

        assertEquals(
            listOf(
                "Bonjour à tous.", "Merci de nous recevoir.", "Nous allons parler du contrat.", "Très bien.",
                "Alors le premier point.", "D'accord pour moi.", "Parfait.",
            ),
            labeled.map { it.text },
        )
        assertEquals(listOf(1, 2, 1, 2, 1, 2, 1), labeled.map { it.speaker })
    }

    @Test
    fun `a speaker silent in the overlap is recognized by voice embedding`() {
        val results = listOf(
            TrancheResult(
                small[0],
                listOf(
                    seg(0f, 50f, "speaker_1", "A"),
                    seg(51f, 79f, "speaker_2", "B"),
                    seg(81f, 88f, "speaker_1", "C"),
                    seg(89.5f, 100f, "speaker_1", "D coupé"),
                ),
            ),
            TrancheResult(
                small[1],
                listOf(
                    seg(80.2f, 88f, "speaker_1", "C"),
                    seg(89.6f, 99f, "speaker_1", "D"),
                    seg(101f, 150f, "speaker_2", "E"),
                ),
            ),
        )
        val embeddings = listOf(
            mapOf("speaker_1" to floatArrayOf(1f, 0f), "speaker_2" to floatArrayOf(0f, 1f)),
            mapOf("speaker_1" to floatArrayOf(1f, 0.1f), "speaker_2" to floatArrayOf(0.05f, 1f)),
        )

        val linked = VoxtralTranches.assemble(results, embeddings)
        assertEquals(listOf("A", "B", "C", "D", "E"), linked.map { it.text })
        assertEquals(listOf(1, 2, 1, 1, 2), linked.map { it.speaker })

        // Without embeddings, E cannot be told apart from a newcomer.
        assertEquals(listOf(1, 2, 1, 1, 3), VoxtralTranches.assemble(results).map { it.speaker })
    }

    @Test
    fun `without diarization every segment is speaker 1`() {
        val results = listOf(
            TrancheResult(
                small[0],
                listOf(seg(0f, 50f, null, "Première idée."), seg(60f, 95f, null, "Deuxième idée longue."), seg(96f, 100f, null, "Fin c")),
            ),
            TrancheResult(small[1], listOf(seg(80.5f, 95f, null, "idée longue."), seg(96f, 170f, null, "Fin."))),
        )
        val labeled = VoxtralTranches.assemble(results)
        assertEquals(listOf("Première idée.", "Deuxième idée longue.", "Fin."), labeled.map { it.text })
        assertTrue(labeled.all { it.speaker == 1 })
    }

    @Test
    fun `paragraphs join one speaker's segments up to a minute`() {
        val labeled = listOf(
            fr.clinitex.transcription.meetings.SpeakerLabeler.LabeledText(0f, 20f, 1, "Un."),
            fr.clinitex.transcription.meetings.SpeakerLabeler.LabeledText(21f, 50f, 1, "Deux."),
            fr.clinitex.transcription.meetings.SpeakerLabeler.LabeledText(51f, 70f, 1, "Trois."),
            fr.clinitex.transcription.meetings.SpeakerLabeler.LabeledText(71f, 80f, 2, "Quatre."),
        )
        val paragraphs = VoxtralTranches.paragraphs(labeled)
        assertEquals(listOf("Un. Deux.", "Trois.", "Quatre."), paragraphs.map { it.text })
        assertEquals(50f, paragraphs[0].endSec, 0f)
    }
}
