package fr.clinitex.transcription.meetings

import fr.clinitex.transcription.ui.notes.NoteBlock
import fr.clinitex.transcription.ui.notes.NotesMarkdown
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Métré export: turns the two tables Claude writes for the
 * [MeetingTemplate.METRE] template into CSV files:
 *  - the premises, imported by the Clinitex costing tool ([COLUMNS]), with
 *    the frequency as a code (Q1…Q5, MENS, TRIM) and the type of premises
 *    reduced to the tool's categories ([LOCAL_TYPES]), the rest of what was
 *    dictated moving to Prestations;
 *  - the sanitary equipment and consumables per premises, sent to the
 *    supplier ([EQUIPMENT_COLUMNS]).
 * Cells not dictated stay empty. Excel (French) format: `;` separator,
 * decimal comma, UTF-8 with BOM, CRLF.
 *
 * Columns are matched by name (accents, case and units ignored), so the
 * export keeps the expected order even if the table's columns move. Pure
 * Kotlin, unit-tested.
 */
object MetreCsv {

    val COLUMNS = listOf("Fréquence", "Batiment", "Etage", "Type de Local", "Revêtement", "Prestations", "Surface")
    val EQUIPMENT_COLUMNS = listOf("Batiment", "Etage", "Type de Local", "Équipement", "Quantité")

    private const val SEPARATOR = ';'
    private const val BOM = "﻿"

    /** What Claude may write for "nothing dictated", despite the instructions. */
    private val emptyMarkers = setOf("-", "—", "–", "/", "rien de noté", "non précisé", "non precise", "n/a", "na")

    /** Premises, one row per local in [COLUMNS] order; empty when the notes hold no Métré table. */
    fun rows(notesMarkdown: String): List<List<String>> {
        val table = tables(notesMarkdown).firstOrNull { t ->
            t.has("Type de Local") && !t.has("Équipement") && (t.has("Surface") || t.has("Revêtement"))
        } ?: return emptyList()
        val type = COLUMNS.indexOf("Type de Local")
        val prestations = COLUMNS.indexOf("Prestations")
        return extract(table, COLUMNS) { column, value ->
            when (column) {
                "Fréquence" -> frequency(value)
                "Surface" -> number(value)
                else -> value
            }
        }.map { row ->
            // "Bureau accueil" → Bureau, and "accueil" goes to Prestations.
            val (category, rest) = splitLocalType(row[type])
            if (category == row[type]) row
            else row.toMutableList().apply {
                set(type, category)
                set(prestations, joinPrestations(rest, row[prestations]))
            }
        }
    }

    /** Categories of the costing tool for "Type de Local" (Stéphane, 22/10/2026). */
    val LOCAL_TYPES = listOf("Accueil", "Circulation", "Bureau", "Salle de réunion", "Vestiaire", "Sanitaire", "Open space", "Réfectoire", "Vitrerie")

    /** Spoken forms (accents removed) → category, longest first. */
    private val localTypeForms: List<Pair<String, String>> = listOf(
        "accueil" to "Accueil", "accueils" to "Accueil",
        "circulation" to "Circulation", "circulations" to "Circulation", "couloir" to "Circulation",
        "couloirs" to "Circulation", "escalier" to "Circulation", "escaliers" to "Circulation",
        "bureau" to "Bureau", "bureaux" to "Bureau",
        "salle de reunion" to "Salle de réunion", "salle de reunions" to "Salle de réunion",
        "salles de reunion" to "Salle de réunion", "salles de reunions" to "Salle de réunion",
        "vestiaire" to "Vestiaire", "vestiaires" to "Vestiaire",
        "sanitaire" to "Sanitaire", "sanitaires" to "Sanitaire", "toilettes" to "Sanitaire", "wc" to "Sanitaire",
        "open space" to "Open space", "open spaces" to "Open space",
        "refectoire" to "Réfectoire", "refectoires" to "Réfectoire",
        "vitrerie" to "Vitrerie", "vitreries" to "Vitrerie",
    ).sortedByDescending { it.first.split(' ').size }

    /**
     * Category of a "Type de Local" cell and what was dictated after it:
     * "Bureau secrétaire" → (Bureau, secrétaire). A cell that starts with no
     * known category is returned unchanged, with nothing left over.
     */
    internal fun splitLocalType(value: String): Pair<String, String> {
        val spoken = key(value, keepSpaces = true)
        for ((form, category) in localTypeForms) {
            if (spoken == form || spoken.startsWith("$form ")) {
                val words = value.trim().split(Regex("[\\s\\-,;:/]+")).filter { it.isNotEmpty() }
                return category to words.drop(form.split(' ').size).joinToString(" ")
            }
        }
        return value to ""
    }

    private fun joinPrestations(extra: String, prestations: String): String = when {
        extra.isBlank() -> prestations
        prestations.isBlank() -> extra
        key(prestations, keepSpaces = true).contains(key(extra, keepSpaces = true)) -> prestations
        else -> "$extra, $prestations"
    }

    /** Sanitary equipment, one row per item and premises in [EQUIPMENT_COLUMNS] order. */
    fun equipmentRows(notesMarkdown: String): List<List<String>> {
        val table = tables(notesMarkdown).firstOrNull { it.has("Équipement") } ?: return emptyList()
        return extract(table, EQUIPMENT_COLUMNS) { column, value -> if (column == "Quantité") number(value) else value }
            .filter { it[EQUIPMENT_COLUMNS.indexOf("Équipement")].isNotEmpty() }
    }

    fun toCsv(rows: List<List<String>>, columns: List<String> = COLUMNS): String = buildString {
        append(BOM)
        append(columns.joinToString(SEPARATOR.toString()) { field(it) }).append("\r\n")
        for (row in rows) append(row.joinToString(SEPARATOR.toString()) { field(it) }).append("\r\n")
    }

    /** "Metre - Résidence Les Tilleuls - 2026-10-22.csv" (characters unsafe in file names removed). */
    fun fileName(title: String, dateMs: Long): String = named("Metre", title, dateMs)

    /** "Equipements sanitaires - Résidence Les Tilleuls - 2026-10-22.csv". */
    fun equipmentFileName(title: String, dateMs: Long): String = named("Equipements sanitaires", title, dateMs)

    /**
     * Frequency code of the costing tool: Q1…Q5 = passages per week (every
     * day = Q5), MENS = once a month, TRIM = once a quarter. Anything else
     * stays as dictated. Claude is asked for the codes; this catches what it
     * leaves in words.
     */
    internal fun frequency(value: String): String {
        val text = key(value, keepSpaces = true)
        if (text.isEmpty()) return value
        Regex("^q\\s?([1-7])$").matchEntire(text)?.let { return "Q" + it.groupValues[1] }
        if (text == "mens" || text == "trim") return text.uppercase(Locale.FRENCH)
        val count = "(1|2|3|4|5|une?|deux|trois|quatre|cinq)"
        Regex("^$count ?(fois|passages?|x) ?(par|a la|/) ?semaine$").matchEntire(text)?.let {
            return "Q" + number(it.groupValues[1])
        }
        if (text.matches(Regex("^(tous les jours|chaque jour|quotidien(ne)?|journalier|(1|une) fois par jour|5 jours sur 7|du lundi au vendredi)$"))) return "Q5"
        if (text.matches(Regex("^(hebdomadaire|chaque semaine|toutes les semaines)$"))) return "Q1"
        if (text.matches(Regex("^((1|une) fois par mois|mensuel(le)?|tous les mois|chaque mois)$"))) return "MENS"
        if (text.matches(Regex("^((1|une) fois par trimestre|trimestriel(le)?|tous les trimestres|chaque trimestre)$"))) return "TRIM"
        return value
    }

    /** "12.5 m²" → "12,5"; anything that is not a plain number is kept as dictated. */
    internal fun number(value: String): String {
        when (value.lowercase(Locale.FRENCH)) {
            "un", "une" -> return "1"
            "deux" -> return "2"
            "trois" -> return "3"
            "quatre" -> return "4"
            "cinq" -> return "5"
        }
        val plain = value.replace(Regex("(?i)\\s*m(²|2)\\s*$"), "").replace(Regex("[\\s  ]"), "")
        return if (Regex("^\\d+([.,]\\d+)?$").matches(plain)) plain.replace('.', ',') else value
    }

    private fun tables(notesMarkdown: String) = NotesMarkdown.parse(notesMarkdown).filterIsInstance<NoteBlock.Table>()

    private fun NoteBlock.Table.has(column: String) = header.any { key(it).startsWith(key(column)) }

    /** The [columns] of [table] (empty when missing), cleaned, without empty rows. */
    private fun extract(table: NoteBlock.Table, columns: List<String>, convert: (String, String) -> String): List<List<String>> {
        val positions = columns.map { column -> table.header.indexOfFirst { key(it).startsWith(key(column)) } }
        return table.rows
            .map { row ->
                columns.mapIndexed { i, column ->
                    val raw = positions[i].takeIf { it >= 0 }?.let { row.getOrNull(it) }.orEmpty()
                    convert(column, cleanCell(raw))
                }
            }
            .filter { row -> row.any { it.isNotEmpty() } }
    }

    private fun named(prefix: String, title: String, dateMs: Long): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), " ").replace(Regex("\\s+"), " ").trim().take(60)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).format(Date(dateMs))
        return if (safe.isEmpty()) "$prefix - $date.csv" else "$prefix - $safe - $date.csv"
    }

    private fun cleanCell(raw: String): String {
        val text = raw.replace("**", "").trim()
        return if (text.lowercase(Locale.FRENCH) in emptyMarkers) "" else text
    }

    private fun field(value: String): String =
        if (value.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    /** Lower case, no accents; letters only ("Surface (m²)" → "surfacem"), or letters, digits and single spaces. */
    internal fun key(text: String, keepSpaces: Boolean = false): String {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.FRENCH)
        return if (keepSpaces) {
            plain.replace(Regex("[^a-z0-9/]+"), " ").trim().replace(Regex("\\s+"), " ")
        } else {
            plain.filter { it.isLetter() }
        }
    }
}
