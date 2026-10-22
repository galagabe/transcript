package fr.clinitex.transcription.meetings

import fr.clinitex.transcription.ui.notes.NoteBlock
import fr.clinitex.transcription.ui.notes.NotesMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Métré: Claude's tables → CSV of the Clinitex costing tool and of the sanitary equipment. */
class MetreCsvTest {

    private val notes = """
        ## Locaux
        | Fréquence | Batiment | Etage | Type de Local | Revêtement | Prestations | Surface |
        |---|---|---|---|---|---|---|
        | Q5 | A | RDC | Bureau | Moquette |  | 12,5 |
        | 5 fois par semaine | A | RDC | Bureau | Moquette | - | 12.5 m² |
        | 2 fois par semaine |  |  | Sanitaires | Carrelage | Rien de noté | 8 |
        |  |  |  | Salle de réunion; grande | Parquet bois |  | environ 40 |

        ## Équipements sanitaires
        | Batiment | Etage | Type de Local | Équipement | Quantité |
        |---|---|---|---|---|
        |  | RDC | Sanitaires | Distributeur de savon | deux |
        |  | RDC | Sanitaires | Désodorisant |  |
        |  |  |  |  |  |

        ## Remarques
        - Accès par badge
    """.trimIndent()

    @Test
    fun `premises become one row per local in the tool's column order`() {
        val rows = MetreCsv.rows(notes)
        assertEquals(4, rows.size)
        assertEquals(listOf("Q5", "A", "RDC", "Bureau", "Moquette", "", "12,5"), rows[0])
        // Words turned into the code, dash and "Rien de noté" emptied, "12.5 m²" with a decimal comma.
        assertEquals(listOf("Q5", "A", "RDC", "Bureau", "Moquette", "", "12,5"), rows[1])
        assertEquals(listOf("Q2", "", "", "Sanitaire", "Carrelage", "", "8"), rows[2])
        // A surface that is not a number is kept as dictated; the rest of the type goes to Prestations.
        assertEquals(listOf("", "", "", "Salle de réunion", "Parquet bois", "grande", "environ 40"), rows[3])
    }

    @Test
    fun `type of premises keeps only the tool's category, the rest goes to Prestations`() {
        val cases = mapOf(
            "Bureau accueil" to ("Bureau" to "accueil"),
            "Bureau secrétaire" to ("Bureau" to "secrétaire"),
            "Bureaux" to ("Bureau" to ""),
            "Couloir 1er étage" to ("Circulation" to "1er étage"),
            "Sanitaires hommes" to ("Sanitaire" to "hommes"),
            "WC" to ("Sanitaire" to ""),
            "Open-space direction" to ("Open space" to "direction"),
            "Salles de réunion" to ("Salle de réunion" to ""),
            "Réfectoire" to ("Réfectoire" to ""),
            "Vitrerie hall" to ("Vitrerie" to "hall"),
            "Accueil" to ("Accueil" to ""),
            // Not one of the categories: kept as dictated.
            "Local archives" to ("Local archives" to ""),
            "Bureautique" to ("Bureautique" to ""),
        )
        for ((dictated, expected) in cases) assertEquals(dictated, expected, MetreCsv.splitLocalType(dictated))

        val table = """
            | Fréquence | Batiment | Etage | Type de Local | Revêtement | Prestations | Surface |
            |---|---|---|---|---|---|---|
            | Q5 |  | RDC | Bureau accueil | Carrelage | vidage corbeilles | 15 |
            | Q5 |  | RDC | Bureau secrétaire | Moquette | secrétaire | 10 |
        """.trimIndent()
        assertEquals(
            listOf(
                listOf("Q5", "", "RDC", "Bureau", "Carrelage", "accueil, vidage corbeilles", "15"),
                // Already in Prestations: not repeated.
                listOf("Q5", "", "RDC", "Bureau", "Moquette", "secrétaire", "10"),
            ),
            MetreCsv.rows(table),
        )
    }

    @Test
    fun `frequencies use the costing tool's codes`() {
        val cases = mapOf(
            "1 fois par semaine" to "Q1",
            "une fois par semaine" to "Q1",
            "trois fois par semaine" to "Q3",
            "3 fois/semaine" to "Q3",
            "Tous les jours." to "Q5",
            "une fois par jour" to "Q5",
            "quotidien" to "Q5",
            "q4" to "Q4",
            "1 fois par mois" to "MENS",
            "mensuel" to "MENS",
            "mens" to "MENS",
            "une fois par trimestre" to "TRIM",
            "Trimestriel" to "TRIM",
            // Not in the agreed table: kept as dictated.
            "2 fois par mois" to "2 fois par mois",
            "3 fois par jour" to "3 fois par jour",
            "" to "",
        )
        for ((dictated, code) in cases) assertEquals(dictated, code, MetreCsv.frequency(dictated))
    }

    @Test
    fun `sanitary equipment is a separate file, by premises and floor`() {
        val equipment = MetreCsv.equipmentRows(notes)
        assertEquals(
            listOf(
                listOf("", "RDC", "Sanitaires", "Distributeur de savon", "2"),
                listOf("", "RDC", "Sanitaires", "Désodorisant", ""),
            ),
            equipment,
        )
        val csv = MetreCsv.toCsv(equipment, MetreCsv.EQUIPMENT_COLUMNS)
        assertTrue(csv.startsWith("﻿Batiment;Etage;Type de Local;Équipement;Quantité\r\n;RDC;Sanitaires;Distributeur de savon;2\r\n"))
        // The equipment table is never taken for the premises table.
        assertTrue(MetreCsv.rows(notes).none { "Distributeur de savon" in it })
        assertTrue(MetreCsv.equipmentRows("## Équipements sanitaires\n\nRien de noté").isEmpty())
    }

    @Test
    fun `columns are matched by name whatever their order, accents or units`() {
        val moved = """
            | Type de local | Surface (m²) | Revêtement | Bâtiment | Étage |
            |:--|--:|---|---|---|
            | Couloir | 30 | Thermoplastique | B | 1 |
        """.trimIndent()
        assertEquals(listOf(listOf("", "B", "1", "Circulation", "Thermoplastique", "", "30")), MetreCsv.rows(moved))
    }

    @Test
    fun `csv is Excel FR ready, quoted where needed`() {
        val csv = MetreCsv.toCsv(MetreCsv.rows(notes))
        assertTrue(csv.startsWith("﻿Fréquence;Batiment;Etage;Type de Local;Revêtement;Prestations;Surface\r\n"))
        assertTrue(csv.contains("\r\nQ5;A;RDC;Bureau;Moquette;;12,5\r\n"))
        assertTrue(csv.contains("\r\n;;;Salle de réunion;Parquet bois;grande;environ 40\r\n"))
        // Separator or quotes inside a cell: quoted.
        assertEquals("\"a;b\";\"dit \"\"oui\"\"\"\r\n", MetreCsv.toCsv(listOf(listOf("a;b", "dit \"oui\"")), listOf("x", "y")).substringAfter("\r\n"))
        assertEquals(5, csv.trimEnd().split("\r\n").size)
    }

    @Test
    fun `notes without a Métré table give nothing to export`() {
        assertTrue(MetreCsv.rows("## Synthèse impossible\n\nErreur").isEmpty())
        assertTrue(MetreCsv.rows("| Nom | Rôle |\n|---|---|\n| Paul | Client |").isEmpty())
    }

    @Test
    fun `file names are safe and dated`() {
        assertEquals("Metre - Résidence Les Tilleuls - 2026-10-22.csv", MetreCsv.fileName("Résidence: Les/Tilleuls", 1_792_656_000_000L))
        assertEquals(
            "Equipements sanitaires - Résidence Les Tilleuls - 2026-10-22.csv",
            MetreCsv.equipmentFileName("Résidence Les Tilleuls", 1_792_656_000_000L),
        )
    }

    @Test
    fun `notes view reads both tables and plain text lists them`() {
        val tables = NotesMarkdown.parse(notes).filterIsInstance<NoteBlock.Table>()
        assertEquals(listOf(7, 5), tables.map { it.header.size })
        val plain = NotesMarkdown.toPlainText(notes)
        assertTrue(plain.contains("• Fréquence : 2 fois par semaine, Type de Local : Sanitaires, Revêtement : Carrelage, Prestations : Rien de noté, Surface : 8"))
        assertTrue(plain.contains("• Etage : RDC, Type de Local : Sanitaires, Équipement : Distributeur de savon, Quantité : deux"))
        assertTrue(plain.contains("Accès par badge"))
    }
}
