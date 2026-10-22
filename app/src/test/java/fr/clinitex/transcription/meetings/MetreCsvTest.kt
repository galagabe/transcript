package fr.clinitex.transcription.meetings

import fr.clinitex.transcription.ui.notes.NoteBlock
import fr.clinitex.transcription.ui.notes.NotesMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Métré: Claude's premises table → CSV of the Clinitex costing tool. */
class MetreCsvTest {

    private val notes = """
        ## Locaux
        | Fréquence | Batiment | Etage | Type de Local | Revêtement | Prestations | Surface |
        |---|---|---|---|---|---|---|
        | 5 fois par semaine | A | RDC | Bureau | Moquette |  | 12,5 |
        | 5 fois par semaine | A | RDC | Bureau | Moquette | - | 12.5 m² |
        | 2 fois par semaine |  |  | Sanitaires | Carrelage | Rien de noté | 8 |
        |  |  |  | Salle de réunion; grande | Parquet bois |  | environ 40 |

        ## Remarques
        - Accès par badge
    """.trimIndent()

    @Test
    fun `the table becomes one row per premises in the tool's column order`() {
        val rows = MetreCsv.rows(notes)
        assertEquals(4, rows.size)
        assertEquals(listOf("5 fois par semaine", "A", "RDC", "Bureau", "Moquette", "", "12,5"), rows[0])
        // Dash and "Rien de noté" are empty cells; "12.5 m²" is a plain number with a decimal comma.
        assertEquals(listOf("5 fois par semaine", "A", "RDC", "Bureau", "Moquette", "", "12,5"), rows[1])
        assertEquals(listOf("2 fois par semaine", "", "", "Sanitaires", "Carrelage", "", "8"), rows[2])
        // A surface that is not a number is kept as dictated.
        assertEquals("environ 40", rows[3][6])
    }

    @Test
    fun `columns are matched by name whatever their order, accents or units`() {
        val moved = """
            | Type de local | Surface (m²) | Revêtement | Bâtiment | Étage |
            |:--|--:|---|---|---|
            | Couloir | 30 | Thermoplastique | B | 1 |
        """.trimIndent()
        assertEquals(listOf(listOf("", "B", "1", "Couloir", "Thermoplastique", "", "30")), MetreCsv.rows(moved))
    }

    @Test
    fun `csv is Excel FR ready, quoted where needed`() {
        val csv = MetreCsv.toCsv(MetreCsv.rows(notes))
        assertTrue(csv.startsWith("﻿Fréquence;Batiment;Etage;Type de Local;Revêtement;Prestations;Surface\r\n"))
        assertTrue(csv.contains("\r\n5 fois par semaine;A;RDC;Bureau;Moquette;;12,5\r\n"))
        assertTrue(csv.contains(";;;\"Salle de réunion; grande\";Parquet bois;;environ 40\r\n"))
        assertEquals(5, csv.trimEnd().split("\r\n").size)
    }

    @Test
    fun `notes without a Métré table give nothing to export`() {
        assertTrue(MetreCsv.rows("## Synthèse impossible\n\nErreur").isEmpty())
        assertTrue(MetreCsv.rows("| Nom | Rôle |\n|---|---|\n| Paul | Client |").isEmpty())
    }

    @Test
    fun `file name is safe and dated`() {
        assertEquals("Metre - Résidence Les Tilleuls - 2026-10-22.csv", MetreCsv.fileName("Résidence: Les/Tilleuls", 1_792_656_000_000L))
    }

    @Test
    fun `notes view reads the table and plain text lists it`() {
        val table = NotesMarkdown.parse(notes).filterIsInstance<NoteBlock.Table>().single()
        assertEquals(7, table.header.size)
        assertEquals(4, table.rows.size)
        val plain = NotesMarkdown.toPlainText(notes)
        assertTrue(plain.contains("• Fréquence : 2 fois par semaine, Type de Local : Sanitaires, Revêtement : Carrelage, Prestations : Rien de noté, Surface : 8"))
        assertTrue(plain.contains("Accès par badge"))
    }
}
