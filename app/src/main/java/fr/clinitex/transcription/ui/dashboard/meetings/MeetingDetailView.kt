package fr.clinitex.transcription.ui.dashboard.meetings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.data.entity.RecordingSession
import fr.clinitex.transcription.meetings.AudioPurgeWorker
import fr.clinitex.transcription.meetings.MeetingTemplate
import fr.clinitex.transcription.ui.notes.MetreExport
import fr.clinitex.transcription.summaries.SummaryRunner
import fr.clinitex.transcription.summaries.SummaryWorker
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.ui.components.BottomActionBar
import fr.clinitex.transcription.ui.components.BrandCard
import fr.clinitex.transcription.ui.components.BrandInput
import fr.clinitex.transcription.ui.components.ConfirmDialog
import fr.clinitex.transcription.ui.components.DisplayTitle
import fr.clinitex.transcription.ui.components.Eyebrow
import fr.clinitex.transcription.ui.components.NoticeBox
import fr.clinitex.transcription.ui.components.NoticeTone
import fr.clinitex.transcription.ui.components.PillButton
import fr.clinitex.transcription.ui.components.PillVariant
import fr.clinitex.transcription.ui.components.RoundIconButton
import fr.clinitex.transcription.ui.components.ScreenBackground
import fr.clinitex.transcription.ui.components.SegmentedPills
import fr.clinitex.transcription.ui.components.StatusBadge
import fr.clinitex.transcription.ui.dashboard.BadgeTone
import fr.clinitex.transcription.ui.dashboard.DisplayFormat
import fr.clinitex.transcription.ui.notes.NotesMarkdown
import fr.clinitex.transcription.ui.notes.TranscriptBlocks
import fr.clinitex.transcription.ui.notes.NotesView
import fr.clinitex.transcription.ui.notes.TranscriptView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Meeting detail (CompteRendu.dc.html): uppercase title, pill tabs
 * "Compte rendu / Transcription / Notes", summary rendered from Markdown,
 * bottom bar "Partager" + "Relancer la synthèse". The Transcription tab
 * (Transcription.dc.html) lets the user name the speakers (lot 3); the bar
 * then holds "Enregistrer les noms".
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

    // Speakers (lot 3): numbers from the stored segments, names given by the user.
    var segmentSpeakers by remember { mutableStateOf(emptyList<Int>()) }
    var savedNames by remember { mutableStateOf(emptyMap<Int, String>()) }
    /** Names being edited; null while the fields show the saved names. */
    var draftNames by remember { mutableStateOf<Map<Int, String>?>(null) }
    var offerRetry by remember { mutableStateOf(false) }

    ScreenBackground(ClinitexPalette.Paper)

    LaunchedEffect(meeting.id) {
        store.getTranscriptForSessionFlow(meeting.id).collect { transcriptText = it?.text }
    }
    LaunchedEffect(meeting.id) {
        store.getSegmentSpeakersFlow(meeting.id).collect { segmentSpeakers = it }
    }
    LaunchedEffect(meeting.id) {
        store.getSpeakerNamesFlow(meeting.id).collect { rows -> savedNames = rows.associate { it.speaker to it.name } }
    }

    // Meetings recorded before lot 3 have no segments: fall back to the "Intervenant N" turns.
    val speakers = remember(segmentSpeakers, transcriptText) {
        segmentSpeakers.ifEmpty { TranscriptBlocks.speakers(transcriptText.orEmpty()) }
    }
    val shownNames = draftNames ?: savedNames
    val namesChanged = draftNames?.let { cleanNames(it) != cleanNames(savedNames) } ?: false

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
            val transcript = transcriptText
            if (!transcript.isNullOrBlank()) {
                append("Transcription\n").append(TranscriptBlocks.withNames(transcript, savedNames))
            }
        }.trim()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, meeting.title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Partager la réunion"))
    }

    // Métré recorded as a meeting in 1.3.0-beta (now a voice note): still exportable.
    val canExportMetre = remember(meeting.summaryText, template) {
        template == MeetingTemplate.METRE && MetreExport.canExport(meeting.summaryText)
    }

    fun exportMetre() = MetreExport.share(context, meeting.title, meeting.startedAt ?: meeting.createdAt, meeting.summaryText)

    fun saveNames() {
        val names = cleanNames(shownNames)
        scope.launch {
            store.saveSpeakerNames(meeting.id, speakers.associateWith { names[it].orEmpty() })
            draftNames = null
            // The summary only sees the names on its next run: offer to regenerate it.
            if (canRetry) offerRetry = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.Paper)
            .imePadding()
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
                    audioRetentionLine(meeting)?.let {
                        Text(it, style = ClinitexType.body(12.sp, FontWeight.Normal, 17.sp), color = ClinitexPalette.Muted)
                    }
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
                                if (speakers.isNotEmpty()) {
                                    SpeakersCard(
                                        speakers = speakers,
                                        names = shownNames,
                                        onNameChange = { speaker, name -> draftNames = shownNames + (speaker to name) },
                                    )
                                }
                                TranscriptView(text, speakerNames = cleanNames(shownNames))
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

        if (activeSection == MeetingSection.TRANSCRIPT && speakers.isNotEmpty() && !transcriptText.isNullOrBlank()) {
            BottomActionBar {
                PillButton(
                    "Enregistrer les noms",
                    onClick = { saveNames() },
                    enabled = namesChanged,
                    modifier = Modifier.weight(1f),
                )
            }
        } else BottomActionBar {
            if (template == MeetingTemplate.METRE) {
                // The text stays shareable from the top bar.
                PillButton(
                    "Exporter les CSV",
                    onClick = { exportMetre() },
                    icon = Icons.Default.IosShare,
                    enabled = canExportMetre,
                    modifier = Modifier.weight(1f),
                )
            } else PillButton(
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

    if (offerRetry) {
        ConfirmDialog(
            title = "Noms enregistrés",
            text = "Relancer la synthèse pour que le compte rendu utilise ces noms ?",
            confirmLabel = "Relancer la synthèse",
            dismissLabel = "Plus tard",
            destructive = false,
            onConfirm = {
                offerRetry = false
                activeSection = MeetingSection.SUMMARY
                scope.launch { SummaryWorker.enqueue(context, meeting.id) }
            },
            onDismiss = { offerRetry = false },
        )
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

/** Trimmed names, blank ones dropped (a blank name means "Intervenant N"). */
private fun cleanNames(names: Map<Int, String>): Map<Int, String> =
    names.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

/**
 * "INTERVENANTS" card (Transcription.dc.html): one numbered avatar per
 * detected speaker, alternately blue and mint, with an editable name.
 */
@Composable
private fun SpeakersCard(
    speakers: List<Int>,
    names: Map<Int, String>,
    onNameChange: (Int, String) -> Unit,
) {
    BrandCard(shadow = false, padding = PaddingValues(16.dp), spacing = 12.dp) {
        Eyebrow("Intervenants")
        speakers.forEach { speaker ->
            val odd = speaker % 2 == 1
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (odd) ClinitexPalette.Blue else ClinitexPalette.Mint),
                ) {
                    Text(
                        speaker.toString(),
                        style = ClinitexType.body(14.sp, FontWeight.Bold, 18.sp),
                        color = if (odd) ClinitexPalette.Paper else ClinitexPalette.Ink,
                    )
                }
                BrandInput(
                    value = names[speaker].orEmpty(),
                    onValueChange = { onNameChange(speaker, it) },
                    placeholder = "Intervenant $speaker",
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Nom de l’intervenant $speaker" },
                )
            }
        }
    }
}

private enum class MeetingSection(val label: String) {
    SUMMARY("Compte rendu"),
    TRANSCRIPT("Transcription"),
    NOTES("Notes"),
}

/**
 * Lot 5 notices under the date line: consent confirmation and the 48 h
 * audio retention ("Audio supprimé automatiquement le 24/10" once purged).
 */
private fun audioRetentionLine(meeting: RecordingSession): String? {
    val consent = meeting.consentAt?.let { "Participants informés le ${DisplayFormat.dayMonthTime(it)}" }
    val audio = when {
        meeting.audioDeletedAt != null ->
            "Audio supprimé automatiquement le ${DisplayFormat.dayMonth(meeting.audioDeletedAt)}"
        meeting.audioFileName != null ->
            AudioPurgeWorker.deletionDueAt(meeting)?.let { "Audio conservé jusqu’au ${DisplayFormat.dayMonthTime(it)}" }
        else -> null
    }
    return listOfNotNull(consent, audio).joinToString(" · ").ifEmpty { null }
}
