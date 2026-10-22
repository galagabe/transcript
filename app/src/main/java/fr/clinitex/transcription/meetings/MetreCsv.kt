package fr.clinitex.transcription.meetings

import fr.clinitex.transcription.ui.notes.NoteBlock
import fr.clinitex.transcription.ui.notes.NotesMarkdown
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Métré export: turns the premises table that Claude writes for the
 * [MeetingTemplate.METRE] template into the CSV imported by the Clinitex
 * costing tool. The columns and their order are the tool's
 * ([COLUMNS]); cells not dictated stay empty. Excel (French) format:
 * `;` separator, decimal comma, UTF-8 with BOM, CRLF.
 *
 * Columns are matched by name (accents, case and units ignored), so the
 * export keeps the tool's order even if the table's columns move. Pure
 * Kotlin, unit-tested.
 */
object MetreCsv {

    val COLUMNS = listOf("Fréquence", "Batiment", "Etage", "Type de Local", "Revêtement", "Prestations", "Surface")

    private const val SEPARATOR = ';'
    private const val BOM = "﻿"
    private val surfaceColumn = COLUMNS.indexOf("Surface")

    /** What Claude may write for "nothing dictated", despite the instructions. */
    private val emptyMarkers = setOf("-", "—", "–", "/", "rien de noté", "non précisé", "non precise", "n/a", "na")

    /** One row per premises, in [COLUMNS] order; empty when the notes hold no Métré table. */
    fun rows(notesMarkdown: String): List<List<String>> {
        val table = NotesMarkdown.parse(notesMarkdown).filterIsInstance<NoteBlock.Table>()
            .firstOrNull { t -> t.header.any { key(it).startsWith(key("Type de Local")) } }
            ?: return emptyList()
        val positions = COLUMNS.map { column -> table.header.indexOfFirst { key(it).startsWith(key(column)) } }
        return table.rows
            .map { row ->
                COLUMNS.indices.map { i ->
                    val raw = positions[i].takeIf { it >= 0 }?.let { row.getOrNull(it) }.orEmpty()
                    val value = cleanCell(raw)
                    if (i == surfaceColumn) surface(value) else value
                }
            }
            .filter { row -> row.any { it.isNotEmpty() } }
    }

    fun toCsv(rows: List<List<String>>): String = buildString {
        append(BOM)
        append(COLUMNS.joinToString(SEPARATOR.toString()) { field(it) }).append("\r\n")
        for (row in rows) append(row.joinToString(SEPARATOR.toString()) { field(it) }).append("\r\n")
    }

    /** "Metre - Résidence Les Tilleuls - 2026-10-22.csv" (characters unsafe in file names removed). */
    fun fileName(title: String, dateMs: Long): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), " ").replace(Regex("\\s+"), " ").trim().take(60)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).format(Date(dateMs))
        return if (safe.isEmpty()) "Metre - $date.csv" else "Metre - $safe - $date.csv"
    }

    /** "12.5 m²" → "12,5"; anything that is not a plain number is kept as dictated. */
    internal fun surface(value: String): String {
        val number = value.replace(Regex("(?i)\\s*m(²|2)\\s*$"), "").replace(Regex("[\\s  ]"), "")
        return if (Regex("^\\d+([.,]\\d+)?$").matches(number)) number.replace('.', ',') else value
    }

    private fun cleanCell(raw: String): String {
        val text = raw.replace("**", "").trim()
        return if (text.lowercase(Locale.FRENCH) in emptyMarkers) "" else text
    }

    private fun field(value: String): String =
        if (value.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    /** Lower case, no accents, letters only: "Surface (m²)" → "surfacem". */
    internal fun key(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.FRENCH)
            .filter { it.isLetter() }
}
