package fr.clinitex.transcription.ui.recording

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.meetings.AudioPurgeWorker
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.theme.MuesliCorners
import fr.clinitex.transcription.ui.components.PillButton
import fr.clinitex.transcription.ui.components.PillVariant

/**
 * Lot 5: shown before every recording (meeting or voice note). Recording
 * only starts once the box is ticked; the confirmation time is stored in
 * RecordingSession.consentAt.
 */
@Composable
fun ConsentDialog(
    isVoiceNote: Boolean,
    onConfirm: (consentAt: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // Unticked every time: consent is confirmed per recording, never remembered.
    var informed by remember { mutableStateOf(false) }
    val retentionHours = AudioPurgeWorker.RETENTION_HOURS
    val intro = if (isVoiceNote) {
        "Si d’autres personnes sont présentes, prévenez-les que vous enregistrez."
    } else {
        "Prévenez les participants que la réunion est enregistrée et transcrite sur ce téléphone."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ClinitexPalette.Paper,
        shape = RoundedCornerShape(MuesliCorners.card),
        title = {
            Text(
                "Avant d’enregistrer",
                style = ClinitexType.body(18.sp, FontWeight.Bold, 24.sp),
                color = ClinitexPalette.Ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "$intro L’audio est supprimé automatiquement au bout de $retentionHours h.",
                    style = ClinitexType.body(14.sp),
                    color = ClinitexPalette.InkSoft,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Checkbox) { informed = !informed },
                ) {
                    Checkbox(
                        checked = informed,
                        // The whole row toggles (bigger touch target, label included).
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = ClinitexPalette.Blue,
                            uncheckedColor = ClinitexPalette.Muted,
                            checkmarkColor = ClinitexPalette.Paper,
                        ),
                    )
                    Text(
                        "Les participants ont été informés de l’enregistrement",
                        style = ClinitexType.body(14.sp, FontWeight.Bold, 19.sp),
                        color = ClinitexPalette.Ink,
                    )
                }
            }
        },
        confirmButton = {
            PillButton(
                "Démarrer",
                onClick = { onConfirm(System.currentTimeMillis()) },
                enabled = informed,
                height = 44.dp,
                fontSize = 14.sp,
            )
        },
        dismissButton = {
            PillButton("Annuler", onClick = onDismiss, variant = PillVariant.OUTLINE, height = 44.dp, fontSize = 14.sp)
        },
    )
}
