package fr.clinitex.transcription.ui.dashboard.voicenotes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.data.entity.RecordingSession
import fr.clinitex.transcription.summaries.SummaryRunner
import fr.clinitex.transcription.summaries.SummaryWorker
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.theme.MuesliCorners
import fr.clinitex.transcription.ui.components.BottomActionBar
import fr.clinitex.transcription.ui.components.ConfirmDialog
import fr.clinitex.transcription.ui.components.DisplayTitle
import fr.clinitex.transcription.ui.components.NoticeBox
import fr.clinitex.transcription.ui.components.NoticeTone
import fr.clinitex.transcription.ui.components.PillButton
import fr.clinitex.transcription.ui.components.PillVariant
import fr.clinitex.transcription.ui.components.RoundIconButton
import fr.clinitex.transcription.ui.components.ScreenBackground
import fr.clinitex.transcription.ui.components.SegmentedPills
import fr.clinitex.transcription.ui.dashboard.DisplayFormat
import fr.clinitex.transcription.ui.dashboard.meetings.MeetingAudioPlayer
import fr.clinitex.transcription.ui.dashboard.meetings.SummarySection
import fr.clinitex.transcription.ui.notes.NotesMarkdown
import fr.clinitex.transcription.ui.notes.TranscriptView
import kotlinx.coroutines.launch
import java.io.File

/**
 * Voice-note detail (VocalDetail.dc.html): player when the WAV is kept,
 * tabs "Note au propre" / "Transcription brute", cleaned note rendered from
 * Markdown, bottom bar "Copier" + "Partager".
 */
@Composable
fun VoiceNoteDetailView(
    store: SharedStore,
    note: RecordingSession,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var transcriptText by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenBackground(ClinitexPalette.Paper)

    LaunchedEffect(note.id) {
        store.getTranscriptForSessionFlow(note.id).collect { transcriptText = it?.text }
    }

    val audioFile = remember(note.audioFileName) {
        note.audioFileName?.let { File(context.filesDir, "meetings/$it") }
    }
    val dateLine = listOfNotNull(
        DisplayFormat.fullDateTime(note.startedAt ?: note.createdAt),
        DisplayFormat.minutesSeconds(note.startedAt, note.endedAt),
        "Vocal",
    ).joinToString(" · ")

    val cleaned = note.summaryState == SummaryRunner.STATE_COMPLETED && note.summaryText.isNotBlank()
    val canRetry = note.phase == "completed" &&
        !transcriptText.isNullOrBlank() &&
        note.summaryState !in setOf(SummaryRunner.STATE_QUEUED, SummaryRunner.STATE_GENERATING)

    /** What Copier / Partager hand over: the cleaned note, or the raw dictation until it exists. */
    fun shareableText(): String =
        if (cleaned) NotesMarkdown.toPlainText(note.summaryText) else transcriptText.orEmpty().trim()

    fun copyNote() {
        val text = shareableText()
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(note.title, text))
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Note copiée", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareNote() {
        val text = shareableText()
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, note.title + "\n\n" + text)
        }
        context.startActivity(Intent.createChooser(intent, "Partager le vocal"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.Paper)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp)) {
            RoundIconButton(Icons.Default.ChevronLeft, "Retour aux vocaux", onBack, iconSize = 28.dp)
            Spacer(modifier = Modifier.weight(1f))
            RoundIconButton(Icons.Outlined.Delete, "Supprimer le vocal", { confirmDelete = true }, tint = ClinitexPalette.Muted, iconSize = 20.dp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayTitle(note.title, size = 24.sp, lineHeight = 25.sp)
                    Text(dateLine, style = ClinitexType.body(13.sp, FontWeight.Normal, 18.sp), color = ClinitexPalette.Muted)
                    if (note.errorMessage != null) {
                        NoticeBox(note.errorMessage, NoticeTone.DANGER, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            if (audioFile != null && audioFile.exists()) {
                item { MeetingAudioPlayer(audioFile, modifier = Modifier.padding(top = 16.dp)) }
            }

            item {
                SegmentedPills(
                    options = listOf("Note au propre", "Transcription brute"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            item {
                Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val raw = transcriptText
                    if (tab == 0) {
                        SummarySection(note, readyLabel = "Note prête", isVoiceNote = true)
                        if (!raw.isNullOrBlank()) DictatedExcerpt(raw)
                        if (canRetry) {
                            PillButton(
                                "Relancer la synthèse",
                                onClick = { scope.launch { SummaryWorker.enqueue(context, note.id) } },
                                variant = PillVariant.OUTLINE,
                                height = 44.dp,
                                fontSize = 14.sp,
                            )
                        }
                    } else if (raw.isNullOrBlank()) {
                        Text(
                            if (note.phase == "completed") "Aucune parole n’a été transcrite." else "Pas encore de transcription.",
                            style = ClinitexType.body(14.sp),
                            color = ClinitexPalette.Muted,
                        )
                    } else {
                        TranscriptView(raw)
                    }
                }
            }
        }

        BottomActionBar {
            PillButton(
                "Copier",
                onClick = { copyNote() },
                icon = Icons.Default.ContentCopy,
                enabled = cleaned || !transcriptText.isNullOrBlank(),
                modifier = Modifier.weight(1f),
            )
            PillButton(
                "Partager",
                onClick = { shareNote() },
                icon = Icons.Default.IosShare,
                variant = PillVariant.OUTLINE,
                enabled = cleaned || !transcriptText.isNullOrBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer ce vocal ?",
            text = "La note, la transcription et l’audio seront supprimés du téléphone.",
            confirmLabel = "Supprimer",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    store.deleteRecordingSessionFully(note)
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Paper-tint box with the start of the raw dictation, in French quotes. */
@Composable
private fun DictatedExcerpt(raw: String) {
    val flat = raw.replace(Regex("\\s+"), " ").trim()
    val excerpt = if (flat.length > 220) flat.take(220).substringBeforeLast(' ') + "…" else flat
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(ClinitexPalette.PaperTint, RoundedCornerShape(MuesliCorners.tile))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("Dicté", style = ClinitexType.body(12.sp, FontWeight.Bold, 16.sp), color = ClinitexPalette.Muted)
        Text(
            "« $excerpt »",
            style = ClinitexType.body(13.sp, FontWeight.Normal, 20.sp),
            color = ClinitexPalette.Muted,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
