package fr.clinitex.transcription.ui.recording

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.meetings.MeetingRecordingController
import fr.clinitex.transcription.meetings.MeetingTemplate
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.ui.components.ConfirmDialog
import fr.clinitex.transcription.ui.components.Eyebrow
import fr.clinitex.transcription.ui.components.MuesliInlineWaveform
import fr.clinitex.transcription.ui.components.MuesliWaveformMode
import fr.clinitex.transcription.ui.components.PillButton
import fr.clinitex.transcription.ui.components.PillVariant
import fr.clinitex.transcription.ui.components.ScreenBackground
import fr.clinitex.transcription.ui.components.brandUpper
import fr.clinitex.transcription.ui.dashboard.DisplayFormat

/**
 * Full-screen recording surface (meetings and voice notes), the only
 * full-blue screen of the app: big timer, white waveform, live transcript
 * card, peach "Arrêter et transcrire" and white-outline "Abandonner".
 */
@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    val controller = MeetingRecordingController
    val finalizing = controller.isFinalizing
    val voiceNote = controller.isVoiceNote
    var confirmDiscard by remember { mutableStateOf(false) }

    ScreenBackground(ClinitexPalette.Blue)
    // Back sends the app to the background: the capture keeps running in its
    // foreground service (notification), it is never stopped by a back gesture.
    BackHandler(enabled = true) { (context as? Activity)?.moveTaskToBack(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.Blue)
    ) {
        // Decorative half disc (brand shape), white at 8 %: no gradient.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = 300.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(-d / 2f, 300.dp.toPx()),
                size = Size(d, d),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (finalizing) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = ClinitexPalette.Paper)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(ClinitexPalette.Peach, CircleShape)
                        )
                    }
                    Eyebrow(
                        when {
                            finalizing -> "Transcription en cours"
                            voiceNote -> "Dictée en cours"
                            else -> "Enregistrement en cours"
                        },
                        color = ClinitexPalette.Paper,
                        tracking = 0.14f,
                    )
                }

                Text(
                    controller.meetingTitle.brandUpper(),
                    style = ClinitexType.display(28.sp, 29.sp),
                    color = ClinitexPalette.Paper,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    if (voiceNote) "Note vocale" else MeetingTemplate.fromId(controller.templateId).label,
                    style = ClinitexType.body(14.sp),
                    color = ClinitexPalette.Paper.copy(alpha = 0.78f),
                    modifier = Modifier.padding(top = 8.dp),
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                ) {
                    Text(
                        DisplayFormat.timer(controller.elapsedSeconds),
                        style = ClinitexType.display(72.sp, 70.sp).copy(fontFeatureSettings = "tnum"),
                        color = ClinitexPalette.Paper,
                        maxLines = 1,
                    )
                    MuesliInlineWaveform(
                        mode = if (finalizing) MuesliWaveformMode.Waiting else MuesliWaveformMode.Level,
                        color = ClinitexPalette.Paper,
                        level = if (finalizing) null else controller.inputLevel,
                        barCount = 20,
                        spacing = 5.dp,
                        minFraction = 0.12f,
                        modifier = Modifier
                            .width(220.dp)
                            .height(56.dp),
                    )
                }

                LiveTranscriptCard(
                    text = controller.liveTranscript,
                    modifier = Modifier.padding(top = 32.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = ClinitexPalette.Paper, modifier = Modifier.size(18.dp))
                    Text(
                        "Transcription sur le téléphone, aucun réseau nécessaire",
                        style = ClinitexType.body(13.sp, FontWeight.Normal, 18.sp),
                        color = ClinitexPalette.Paper.copy(alpha = 0.78f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            if (finalizing) {
                Text(
                    if (voiceNote) "Transcription de la fin de la dictée…"
                    else "Transcription de l’audio restant, puis séparation des intervenants…",
                    style = ClinitexType.body(14.sp, FontWeight.Medium, 20.sp),
                    color = ClinitexPalette.Paper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }
            PillButton(
                text = if (finalizing) "Transcription…" else "Arrêter et transcrire",
                onClick = { MeetingRecordingController.stop(context) },
                variant = PillVariant.PEACH,
                icon = Icons.Filled.Stop,
                loading = finalizing,
                height = 56.dp,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Abandonner",
                onClick = { confirmDiscard = true },
                variant = PillVariant.ON_BLUE_OUTLINE,
                enabled = !finalizing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = if (voiceNote) "Abandonner la dictée ?" else "Abandonner l’enregistrement ?",
            text = "L’audio et la transcription en cours seront supprimés du téléphone.",
            confirmLabel = "Abandonner",
            dismissLabel = "Continuer",
            onConfirm = {
                confirmDiscard = false
                MeetingRecordingController.discard(context)
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun LiveTranscriptCard(text: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(ClinitexPalette.Paper, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Eyebrow("Transcription en direct", size = 11.sp)
        Text(
            liveTail(text).ifEmpty { "Le texte apparaît ici au fil de la parole, après chaque pause." },
            style = ClinitexType.body(14.sp, FontWeight.Normal, 22.sp),
            color = if (text.isBlank()) ClinitexPalette.Muted else ClinitexPalette.InkSoft,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Last ~240 characters of the live transcript, cut on a word boundary. */
private fun liveTail(text: String, max: Int = 240): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.length <= max) return flat
    val cut = flat.takeLast(max)
    val space = cut.indexOf(' ')
    return "… " + if (space in 0..40) cut.substring(space + 1) else cut
}
