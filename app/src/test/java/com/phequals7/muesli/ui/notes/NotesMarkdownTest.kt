package com.phequals7.muesli.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesMarkdownTest {

    @Test
    fun parsesHeadingsTasksBulletsAndParagraphs() {
        val md = """
            ## Note
            À partir de lundi, trois points
            pour l'équipe du soir.

            ## À faire
            - [ ] Vérifier les fenêtres du 2e étage
            - [x] Commander le produit
            - Un point simple
            1. Premier
            2) Second
        """.trimIndent()

        val blocks = NotesMarkdown.parse(md)

        assertEquals(
            listOf(
                NoteBlock.Heading("Note", 2),
                NoteBlock.Paragraph("À partir de lundi, trois points pour l'équipe du soir."),
                NoteBlock.Heading("À faire", 2),
                NoteBlock.Task("Vérifier les fenêtres du 2e étage", checked = false),
                NoteBlock.Task("Commander le produit", checked = true),
                NoteBlock.Bullet("Un point simple"),
                NoteBlock.Bullet("Premier", "1."),
                NoteBlock.Bullet("Second", "2."),
            ),
            blocks,
        )
    }

    @Test
    fun boldLineIsNotABullet_andRulesAndDecorationsAreDropped() {
        val md = "# **Synthèse** #\n---\n**Commercial Clinitex** · envoyer le devis\n> cité"
        val blocks = NotesMarkdown.parse(md)
        assertEquals(
            listOf(
                NoteBlock.Heading("Synthèse", 1),
                NoteBlock.Paragraph("**Commercial Clinitex** · envoyer le devis cité"),
            ),
            blocks,
        )
    }

    @Test
    fun emptyInputGivesNoBlocks() {
        assertEquals(emptyList<NoteBlock>(), NotesMarkdown.parse("  \n\r\n "))
    }

    @Test
    fun inlineBoldSpans() {
        assertEquals(
            listOf(InlineSpan("Prospect", true), InlineSpan(" · présenter le devis", false)),
            NotesMarkdown.inline("**Prospect** · présenter le devis"),
        )
        // Unmatched marker stays literal.
        assertEquals(listOf(InlineSpan("a **b", false)), NotesMarkdown.inline("a **b"))
        assertEquals(
            listOf(InlineSpan("a", true), InlineSpan(" **b", false)),
            NotesMarkdown.inline("**a** **b"),
        )
    }

    @Test
    fun plainTextForSharing() {
        val md = "## Note\nBonjour **à tous**.\n\n## À faire\n- [ ] Appeler\n- Relancer\n\nFin"
        assertEquals(
            "Note\nBonjour à tous.\n\nÀ faire\n☐ Appeler\n• Relancer\n\nFin",
            NotesMarkdown.toPlainText(md),
        )
    }

    @Test
    fun transcriptBlocksGroupSpeakerTurns() {
        val transcript = "Intervenant 1 · 0:04\nBonjour.\n\nSuite du même tour.\n\nIntervenant 2 · 0:31\nTrès bien."
        val blocks = TranscriptBlocks.parse(transcript)
        assertEquals(2, blocks.size)
        assertEquals(TranscriptBlock("Intervenant 1", 1, "0:04", "Bonjour.\n\nSuite du même tour."), blocks[0])
        assertEquals(TranscriptBlock("Intervenant 2", 2, "0:31", "Très bien."), blocks[1])
    }

    @Test
    fun transcriptWithoutLabelsStaysPlain() {
        val blocks = TranscriptBlocks.parse("Premier paragraphe.\n\nSecond paragraphe.")
        assertEquals(2, blocks.size)
        assertNull(blocks[0].speaker)
        assertEquals("Second paragraphe.", blocks[1].text)
    }
}
