package fr.clinitex.transcription.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.ui.components.Eyebrow

/**
 * Renders summary / cleaned-note Markdown the Clinitex way: headings as blue
 * uppercase eyebrows, "- [ ]" lines as checkboxes (not persisted: they reset
 * when the screen is reopened), bullets, paragraphs with **bold** runs, and
 * table rows (Métré) as small cards.
 */
@Composable
fun NotesView(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { NotesMarkdown.parse(markdown) }
    val checked = remember(markdown) { mutableStateMapOf<Int, Boolean>() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is NoteBlock.Heading -> Eyebrow(
                    block.text,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp),
                )
                is NoteBlock.Paragraph -> Text(
                    inlineText(block.text),
                    style = ClinitexType.body(14.sp, FontWeight.Normal, 22.sp),
                    color = ClinitexPalette.InkSoft,
                )
                is NoteBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        block.marker,
                        style = ClinitexType.body(14.sp, FontWeight.Bold, 21.sp),
                        color = ClinitexPalette.Blue,
                        modifier = Modifier.width(if (block.marker == "•") 10.dp else 20.dp),
                    )
                    Text(
                        inlineText(block.text),
                        style = ClinitexType.body(14.sp, FontWeight.Normal, 21.sp),
                        color = ClinitexPalette.Ink,
                        modifier = Modifier.weight(1f),
                    )
                }
                is NoteBlock.Task -> {
                    val isChecked = checked[index] ?: block.checked
                    TaskRow(
                        text = block.text,
                        checked = isChecked,
                        onToggle = { checked[index] = !isChecked },
                    )
                }
                is NoteBlock.Table -> TableRows(block)
            }
        }
    }
}

@Composable
private fun TaskRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) ClinitexPalette.Blue else Color.Transparent)
                .border(1.5.dp, ClinitexPalette.Blue, RoundedCornerShape(4.dp)),
        ) {
            if (checked) {
                Icon(Icons.Default.Check, contentDescription = null, tint = ClinitexPalette.Paper, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            inlineText(text),
            style = ClinitexType.body(14.sp, FontWeight.Normal, 20.sp).copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (checked) ClinitexPalette.Muted else ClinitexPalette.Ink,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A table is too wide for a phone: one card per row, "Column : value" for the filled cells. */
@Composable
private fun TableRows(table: NoteBlock.Table) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        table.rows.forEach { row ->
            val text = buildAnnotatedString {
                table.header.zip(row).filter { it.second.isNotBlank() }.forEachIndexed { i, (column, value) ->
                    if (i > 0) append("  ·  ")
                    withStyle(SpanStyle(color = ClinitexPalette.Muted)) { append("$column ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.replace("**", "")) }
                }
            }
            Text(
                text,
                style = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp),
                color = ClinitexPalette.Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClinitexPalette.PaperTint)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

private fun inlineText(text: String): AnnotatedString = buildAnnotatedString {
    NotesMarkdown.inline(text).forEach { span ->
        if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
    }
}

/**
 * Transcript rendering: speaker turns ("Intervenant 1 · 0:04", or the name
 * given in [speakerNames]) as small bold labels, odd speakers in blue and
 * even ones in ink and indented, text below. Selectable.
 */
@Composable
fun TranscriptView(transcript: String, modifier: Modifier = Modifier, speakerNames: Map<Int, String> = emptyMap()) {
    val blocks = remember(transcript) { TranscriptBlocks.parse(transcript) }
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            blocks.forEach { block ->
                val odd = (block.speakerNumber ?: 1) % 2 == 1
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = if (odd) 0.dp else 14.dp),
                ) {
                    val label = TranscriptBlocks.label(block, speakerNames)
                    if (label != null) {
                        Text(
                            "$label · ${block.time}",
                            style = ClinitexType.body(12.sp, FontWeight.Bold, 16.sp),
                            color = if (odd) ClinitexPalette.Blue else ClinitexPalette.Ink,
                        )
                    }
                    if (block.text.isNotEmpty()) {
                        Text(
                            block.text,
                            style = ClinitexType.body(14.sp, FontWeight.Normal, 22.sp),
                            color = ClinitexPalette.InkSoft,
                        )
                    }
                }
            }
        }
    }
}
