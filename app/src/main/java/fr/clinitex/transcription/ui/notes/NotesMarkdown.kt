package fr.clinitex.transcription.ui.notes

/**
 * Minimal Markdown reader for the notes returned by the summary webhook
 * (meeting summaries and cleaned-up voice notes). Claude writes "## Heading",
 * "- [ ] task", "- bullet", "1. item", plain paragraphs and, for the Métré
 * template, one `| … |` table; anything else is shown as text. Pure Kotlin so it is unit-tested on the JVM.
 */
sealed interface NoteBlock {
    data class Heading(val text: String, val level: Int) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
    /** [marker] is "•" for bullets, "1." / "2." … for numbered items. */
    data class Bullet(val text: String, val marker: String = "•") : NoteBlock
    data class Task(val text: String, val checked: Boolean) : NoteBlock
    /** Markdown table (Métré): [rows] have exactly as many cells as [header]. */
    data class Table(val header: List<String>, val rows: List<List<String>>) : NoteBlock
}

/** A run of inline text, bold when it was wrapped in `**`. */
data class InlineSpan(val text: String, val bold: Boolean)

object NotesMarkdown {

    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val taskRegex = Regex("^[-*+•]\\s+\\[([ xX])]\\s*(.*)$")
    private val bulletRegex = Regex("^[-*+•]\\s+(.*)$")
    private val numberedRegex = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
    private val ruleRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")

    fun parse(markdown: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        val paragraph = mutableListOf<String>()
        val tableLines = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += NoteBlock.Paragraph(paragraph.joinToString(" "))
                paragraph.clear()
            }
        }

        fun flushTable() {
            if (tableLines.isNotEmpty()) {
                table(tableLines)?.let { blocks += it }
                tableLines.clear()
            }
        }

        for (raw in markdown.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            if (line.startsWith("|")) {
                flushParagraph()
                tableLines += line
                continue
            }
            flushTable()
            if (line.isEmpty() || ruleRegex.matches(line)) {
                flushParagraph()
                continue
            }
            val heading = headingRegex.matchEntire(line)
            if (heading != null) {
                flushParagraph()
                val text = cleanHeading(heading.groupValues[2])
                if (text.isNotEmpty()) blocks += NoteBlock.Heading(text, heading.groupValues[1].length)
                continue
            }
            val task = taskRegex.matchEntire(line)
            if (task != null) {
                flushParagraph()
                blocks += NoteBlock.Task(task.groupValues[2].trim(), checked = task.groupValues[1] != " ")
                continue
            }
            val bullet = bulletRegex.matchEntire(line)
            if (bullet != null) {
                flushParagraph()
                blocks += NoteBlock.Bullet(bullet.groupValues[1].trim())
                continue
            }
            val numbered = numberedRegex.matchEntire(line)
            if (numbered != null) {
                flushParagraph()
                blocks += NoteBlock.Bullet(numbered.groupValues[2].trim(), "${numbered.groupValues[1]}.")
                continue
            }
            paragraph += if (line.startsWith(">")) line.removePrefix(">").trim() else line
        }
        flushTable()
        flushParagraph()
        return blocks
    }

    private val tableSeparatorRegex = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")
    private val cellSplitRegex = Regex("(?<!\\\\)\\|")

    /** Header + rows of consecutive `| a | b |` lines; the `|---|` line is skipped. */
    private fun table(lines: List<String>): NoteBlock.Table? {
        val cells = lines.filterNot { tableSeparatorRegex.matches(it) }.map { line ->
            line.removePrefix("|").let { if (it.endsWith("|") && !it.endsWith("\\|")) it.dropLast(1) else it }
                .split(cellSplitRegex)
                .map { it.replace("\\|", "|").trim() }
        }
        val header = cells.firstOrNull() ?: return null
        val rows = cells.drop(1)
            .map { row -> List(header.size) { row.getOrElse(it) { "" } } }
            .filter { row -> row.any { it.isNotBlank() } }
        return NoteBlock.Table(header, rows)
    }

    /** Splits `**bold**` runs; an unmatched `**` is kept as literal text. */
    fun inline(text: String): List<InlineSpan> {
        val parts = text.split("**")
        if (parts.size == 1) return listOf(InlineSpan(text, false))
        val spans = mutableListOf<InlineSpan>()
        // Odd part count = every marker is paired. Otherwise the last marker is literal.
        val paired = if (parts.size % 2 == 1) parts else parts.dropLast(2) + (parts[parts.size - 2] + "**" + parts.last())
        paired.forEachIndexed { index, part ->
            if (part.isNotEmpty()) spans += InlineSpan(part, bold = index % 2 == 1)
        }
        return spans
    }

    /** Text without Markdown markers, for copy / share (Outlook, Teams, SMS). */
    fun toPlainText(markdown: String): String {
        val out = StringBuilder()
        var previous: NoteBlock? = null
        for (block in parse(markdown)) {
            val line = when (block) {
                is NoteBlock.Heading -> plain(block.text)
                is NoteBlock.Paragraph -> plain(block.text)
                is NoteBlock.Bullet -> "${block.marker} ${plain(block.text)}"
                is NoteBlock.Task -> (if (block.checked) "☒ " else "☐ ") + plain(block.text)
                is NoteBlock.Table -> block.rows.joinToString("\n") { row ->
                    "• " + block.header.zip(row).filter { it.second.isNotBlank() }
                        .joinToString(", ") { (h, v) -> "${plain(h)} : ${plain(v)}" }
                }
            }
            if (previous != null) {
                val tight = previous is NoteBlock.Heading || (isListItem(block) && isListItem(previous))
                out.append(if (tight) "\n" else "\n\n")
            }
            out.append(line)
            previous = block
        }
        return out.toString()
    }

    private fun isListItem(block: NoteBlock) = block is NoteBlock.Bullet || block is NoteBlock.Task

    private fun plain(text: String): String = inline(text).joinToString("") { it.text }

    private fun cleanHeading(text: String): String =
        text.trim().trimEnd('#').trim().removeSurrounding("**").trim()
}

/** One speaker turn of a diarized transcript ("Intervenant 2 · 0:31\n…"). */
data class TranscriptBlock(val speaker: String?, val speakerNumber: Int?, val time: String?, val text: String)

object TranscriptBlocks {

    private val headerRegex = Regex("^((?:Intervenant|Speaker) (\\d+))\\s*·\\s*(\\d+:\\d{2}(?::\\d{2})?)$")

    /**
     * Splits a transcript rendered by SpeakerLabeler into speaker turns.
     * Paragraphs without a header continue the previous turn; a transcript
     * without any header (voice notes, diarization off) gives unlabeled blocks.
     */
    fun parse(transcript: String): List<TranscriptBlock> {
        val blocks = mutableListOf<TranscriptBlock>()
        val paragraphs = transcript.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue
            val firstLine = trimmed.substringBefore('\n').trim()
            val header = headerRegex.matchEntire(firstLine)
            if (header != null) {
                val body = if (trimmed.contains('\n')) trimmed.substringAfter('\n').trim() else ""
                blocks += TranscriptBlock(
                    speaker = header.groupValues[1],
                    speakerNumber = header.groupValues[2].toIntOrNull(),
                    time = header.groupValues[3],
                    text = body,
                )
            } else if (blocks.isNotEmpty() && blocks.last().speaker != null) {
                val last = blocks.removeAt(blocks.lastIndex)
                blocks += last.copy(text = if (last.text.isEmpty()) trimmed else last.text + "\n\n" + trimmed)
            } else {
                blocks += TranscriptBlock(null, null, null, trimmed)
            }
        }
        return blocks
    }

    /** Speaker numbers found in the turn headers, in ascending order. */
    fun speakers(transcript: String): List<Int> =
        parse(transcript).mapNotNull { it.speakerNumber }.distinct().sorted()

    /** Label of a turn: the user-given name, else "Intervenant N" as stored. */
    fun label(block: TranscriptBlock, names: Map<Int, String>): String? =
        block.speakerNumber?.let { names[it]?.trim()?.takeIf(String::isNotEmpty) } ?: block.speaker

    /**
     * Transcript text with renamed speakers ("Prospect · 0:31" instead of
     * "Intervenant 2 · 0:31"), for Copier / Partager. Unchanged without names.
     */
    fun withNames(transcript: String, names: Map<Int, String>): String {
        if (names.values.none { it.isNotBlank() }) return transcript
        return parse(transcript).joinToString("\n\n") { block ->
            val label = label(block, names)
            when {
                label == null -> block.text
                block.text.isEmpty() -> "$label · ${block.time}"
                else -> "$label · ${block.time}\n${block.text}"
            }
        }
    }
}
