package fr.clinitex.transcription.ui.dashboard.voicenotes

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.data.entity.RecordingSession
import fr.clinitex.transcription.meetings.MeetingRecordingController
import fr.clinitex.transcription.meetings.MeetingTemplate
import fr.clinitex.transcription.model.ModelManager
import fr.clinitex.transcription.model.SpeechModels
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.ui.components.BrandCard
import fr.clinitex.transcription.ui.components.EmptyState
import fr.clinitex.transcription.ui.components.NoticeBox
import fr.clinitex.transcription.ui.components.NoticeTone
import fr.clinitex.transcription.ui.components.ScreenBackground
import fr.clinitex.transcription.ui.components.ScreenHeader
import fr.clinitex.transcription.ui.components.brandUpper
import fr.clinitex.transcription.ui.dashboard.DisplayFormat
import fr.clinitex.transcription.ui.dashboard.SummaryRecoveryBanner
import fr.clinitex.transcription.ui.dashboard.meetings.SessionRow
import fr.clinitex.transcription.ui.recording.ConsentDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Vocaux tab (Vocaux.dc.html): one big button to dictate, then the recent
 * voice notes. A voice note is a RecordingSession with kind "voiceNote",
 * transcribed on the phone and cleaned up by the same deferred summary
 * pipeline as meetings (template "noteVocale").
 */
@Composable
fun VoiceNotesTab(
    store: SharedStore,
    onOpenNote: (String) -> Unit,
) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(emptyList<RecordingSession>()) }
    var askConsent by remember { mutableStateOf(false) }
    val modelReady = remember { ModelManager(context.applicationContext).isDownloaded() }

    ScreenBackground(ClinitexPalette.PaperTint)

    LaunchedEffect(Unit) {
        store.getRecordingSessionsFlow(RecordingSession.KIND_VOICE_NOTE).collect { notes = it }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startDictation(consentAt: Long) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val title = "Vocal du ${SimpleDateFormat("d MMM 'à' HH:mm", Locale.FRENCH).format(Date())}"
        MeetingRecordingController.start(
            context,
            UUID.randomUUID().toString(),
            title,
            MeetingTemplate.NOTE_VOCALE.id,
            RecordingSession.KIND_VOICE_NOTE,
            consentAt,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.PaperTint),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
    ) {
        item {
            ScreenHeader(
                "Vocaux",
                intro = "Dictez une consigne, une idée ou un retour de visite. Clindy le transcrit puis le met au propre.",
            )
        }
        item { SummaryRecoveryBanner(notes, Modifier.padding(top = 14.dp)) }

        item {
            BrandCard(
                modifier = Modifier.padding(top = 18.dp),
                radius = 24.dp,
                padding = PaddingValues(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 22.dp),
                spacing = 14.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DictateButton(enabled = modelReady, onClick = { askConsent = true })
                    Text(
                        "Appuyez pour dicter".brandUpper(),
                        style = ClinitexType.eyebrow(14.sp, 0.08f),
                        color = ClinitexPalette.Blue,
                    )
                    if (modelReady) {
                        Text(
                            "Sans réseau, la note se met au propre dès que le téléphone est reconnecté.",
                            style = ClinitexType.body(12.sp, FontWeight.Normal, 17.sp),
                            color = ClinitexPalette.Muted,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        NoticeBox(
                            "Le modèle ${SpeechModels.selected(context).shortName} est nécessaire : téléchargez-le d’abord dans Réglages.",
                            NoticeTone.WARNING,
                        )
                    }
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 12.dp),
            ) {
                Text(
                    "Vocaux récents".brandUpper(),
                    style = ClinitexType.display(16.sp, 18.sp),
                    color = ClinitexPalette.Blue,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DisplayFormat.savedCount(notes.size, feminine = false),
                    style = ClinitexType.body(13.sp, FontWeight.Normal, 18.sp),
                    color = ClinitexPalette.Muted,
                )
            }
        }

        if (notes.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.GraphicEq,
                    title = "Aucun vocal pour l’instant",
                    detail = "Appuyez sur le micro pour dicter votre premier vocal.",
                )
            }
        } else {
            items(notes, key = { it.id }) { note ->
                SessionRow(
                    session = note,
                    icon = Icons.Filled.GraphicEq,
                    meta = listOfNotNull(
                        DisplayFormat.relativeDayTime(note.startedAt ?: note.createdAt),
                        DisplayFormat.minutesSeconds(note.startedAt, note.endedAt),
                    ).joinToString(" · "),
                    onClick = { onOpenNote(note.id) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }

    if (askConsent) {
        ConsentDialog(
            isVoiceNote = true,
            onConfirm = { consentAt -> askConsent = false; startDictation(consentAt) },
            onDismiss = { askConsent = false },
        )
    }
}

/** 132dp blue disc with a 10dp mint ring and a white microphone. */
@Composable
private fun DictateButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(132.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(ClinitexPalette.Blue)
            .border(10.dp, ClinitexPalette.Mint, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Enregistrer un vocal" },
    ) {
        Icon(Icons.Default.Mic, contentDescription = null, tint = ClinitexPalette.Paper, modifier = Modifier.size(44.dp))
    }
}
