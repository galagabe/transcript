package com.phequals7.muesli.ui.dashboard.meetings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.meetings.MeetingTemplate
import com.phequals7.muesli.summaries.SummaryRunner
import com.phequals7.muesli.summaries.SummaryWorker
import com.phequals7.muesli.theme.ClinitexPalette
import com.phequals7.muesli.theme.ClinitexType
import com.phequals7.muesli.ui.components.BottomActionBar
import com.phequals7.muesli.ui.components.BrandInput
import com.phequals7.muesli.ui.components.ConfirmDialog
import com.phequals7.muesli.ui.components.DisplayTitle
import com.phequals7.muesli.ui.components.NoticeBox
import com.phequals7.muesli.ui.components.NoticeTone
import com.phequals7.muesli.ui.components.PillButton
import com.phequals7.muesli.ui.components.PillVariant
import com.phequals7.muesli.ui.components.RoundIconButton
import com.phequals7.muesli.ui.components.ScreenBackground
import com.phequals7.muesli.ui.components.SegmentedPills
import com.phequals7.muesli.ui.components.StatusBadge
import com.phequals7.muesli.ui.dashboard.BadgeTone
import com.phequals7.muesli.ui.dashboard.DisplayFormat
import com.phequals7.muesli.ui.notes.NotesMarkdown
import com.phequals7.muesli.ui.notes.NotesView
import com.phequals7.muesli.ui.notes.TranscriptView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Meeting detail (CompteRendu.dc.html): uppercase title, pill tabs
 * "Compte rendu / Transcription / Notes", summary rendered from Markdown,
 * bottom bar "Partager" + "Relancer la synthèse".
 */
@Composable
fun MeetingDetailView(
    store: SharedStore,
    meeting: RecordingSession,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var transcriptText by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(meeting.manualNotes) }
    var activeSection by remember {
        mutableStateOf(if (meeting.summaryText.isNotBlank()) MeetingSection.SUMMARY else MeetingSection.TRANSCRIPT)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenBackground(ClinitexPalette.Paper)

    LaunchedEffect(meeting.id) {
        store.getTranscriptForSessionFlow(meeting.id).collect { transcriptText = it?.text }
    }

    // Debounced notes persistence
    LaunchedEffect(notes) {
        if (notes == meeting.manualNotes) return@LaunchedEffect
        delay(600)
        // Re-read the row: a summary may have landed meanwhile and must not be overwritten.
        store.getRecordingSessionById(meeting.id)?.let { store.updateRecordingSession(it.copy(manualNotes = notes)) }
    }

    val template = MeetingTemplate.fromId(meeting.templateId)
    // Retained meeting audio file, if audio retention kept one on disk.
    val audioFile = remember(meeting.audioFileName) {
        meeting.audioFileName?.let { File(context.filesDir, "meetings/$it") }
    }
    val dateLine = listOfNotNull(
        DisplayFormat.fullDateTime(meeting.startedAt ?: meeting.createdAt),
        DisplayFormat.minutes(meeting.startedAt, meeting.endedAt),
        template.label,
    ).joinToString(" · ")

    val canRetry = meeting.phase == "completed" &&
        !transcriptText.isNullOrBlank() &&
        meeting.summaryState !in setOf(SummaryRunner.STATE_QUEUED, SummaryRunner.STATE_GENERATING)

    fun shareMeeting() {
        val body = buildString {
            append(meeting.title).append("\n").append(dateLine).append("\n\n")
            if (meeting.summaryText.isNotBlank()) append(NotesMarkdown.toPlainText(meeting.summaryText)).append("\n\n")
            if (notes.isNotBlank()) append("Notes saisies\n").append(notes.trim()).append("\n\n")
            if (!transcriptText.isNullOrBlank()) append("Transcription\n").append(transcriptText)
        }.trim()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, meeting.title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Partager la réunion"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.Paper)
    ) {
        // Top bar: back / share / delete
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp)) {
            RoundIconButton(Icons.Default.ChevronLeft, "Retour aux réunions", onBack, iconSize = 28.dp)
            Spacer(modifier = Modifier.weight(1f))
            RoundIconButton(Icons.Outlined.Share, "Partager", { shareMeeting() }, iconSize = 20.dp)
            RoundIconButton(Icons.Outlined.Delete, "Supprimer la réunion", { confirmDelete = true }, tint = ClinitexPalette.Muted, iconSize = 20.dp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayTitle(meeting.title, size = 24.sp, lineHeight = 25.sp)
                    Text(dateLine, style = ClinitexType.body(13.sp, FontWeight.Normal, 18.sp), color = ClinitexPalette.Muted)
                    if (meeting.errorMessage != null) {
                        NoticeBox(meeting.errorMessage, NoticeTone.DANGER, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            // Retained meeting audio playback (only when a WAV was actually kept)
            if (audioFile != null && audioFile.exists()) {
                item { MeetingAudioPlayer(audioFile, modifier = Modifier.padding(top = 16.dp)) }
            }

            item {
                SegmentedPills(
                    options = MeetingSection.entries.map { it.label },
                    selectedIndex = activeSection.ordinal,
                    onSelect = { activeSection = MeetingSection.entries[it] },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (activeSection) {
                        MeetingSection.SUMMARY -> SummarySection(meeting)
                        MeetingSection.TRANSCRIPT -> {
                            val text = transcriptText
                            if (text.isNullOrBlank()) {
                                Text(
                                    if (meeting.phase == "completed") "Aucune parole n’a été transcrite." else "Pas encore de transcription.",
                                    style = ClinitexType.body(14.sp),
                                    color = ClinitexPalette.Muted,
                                )
                            } else {
                                TranscriptView(text)
                            }
                        }
                        MeetingSection.NOTES -> BrandInput(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = "Saisissez vos notes pour cette réunion…",
                            singleLine = false,
                            minHeight = 200.dp,
                        )
                    }
                }
            }
        }

        BottomActionBar {
            PillButton(
                "Partager",
                onClick = { shareMeeting() },
                icon = Icons.Default.IosShare,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                "Relancer la synthèse",
                onClick = { scope.launch { SummaryWorker.enqueue(context, meeting.id) } },
                variant = PillVariant.OUTLINE,
                enabled = canRetry,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer la réunion ?",
            text = "Le compte rendu, la transcription, les notes et l’audio seront supprimés du téléphone.",
            confirmLabel = "Supprimer",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    store.deleteRecordingSessionFully(meeting)
                    onDeleted()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Summary tab body: status badge, then the rendered notes or the pending/failed state. */
@Composable
internal fun SummarySection(session: RecordingSession, readyLabel: String = "Compte rendu prêt", isVoiceNote: Boolean = false) {
    when (session.summaryState) {
        SummaryRunner.STATE_COMPLETED -> {
            StatusBadge(readyLabel, BadgeTone.READY, withCheck = true)
            NotesView(session.summaryText)
        }
        SummaryRunner.STATE_FAILED -> {
            StatusBadge(if (isVoiceNote) "Mise au propre impossible" else "Synthèse impossible", BadgeTone.FAILED)
            NotesView(session.summaryText)
        }
        SummaryRunner.STATE_GENERATING -> {
            StatusBadge(if (isVoiceNote) "Mise au propre en cours" else "Rédaction en cours", BadgeTone.PENDING, loading = true)
            Text(
                if (isVoiceNote) "La note est en cours de mise au propre, elle arrive dans quelques instants."
                else "Le compte rendu est en cours de rédaction, il arrive dans quelques instants.",
                style = ClinitexType.body(14.sp),
                color = ClinitexPalette.InkSoft,
            )
        }
        SummaryRunner.STATE_QUEUED -> {
            StatusBadge("En attente de réseau", BadgeTone.PENDING)
            Text(
                if (isVoiceNote) "La note sera mise au propre automatiquement dès que le téléphone sera connecté."
                else "Le compte rendu sera généré automatiquement dès que le téléphone sera connecté.",
                style = ClinitexType.body(14.sp),
                color = ClinitexPalette.InkSoft,
            )
        }
        else -> Text(
            if (isVoiceNote) "Aucune note mise au propre pour ce vocal." else "Aucun compte rendu pour cette réunion.",
            style = ClinitexType.body(14.sp),
            color = ClinitexPalette.Muted,
        )
    }
}

private enum class MeetingSection(val label: String) {
    SUMMARY("Compte rendu"),
    TRANSCRIPT("Transcription"),
    NOTES("Notes"),
}
