package fr.clinitex.transcription.ui.dashboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.data.entity.RecordingSession
import fr.clinitex.transcription.summaries.SummaryRecoveryWorker
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.theme.MuesliCorners
import fr.clinitex.transcription.ui.components.NoticeBox
import fr.clinitex.transcription.ui.components.NoticeTone
import fr.clinitex.transcription.ui.components.PillButton
import fr.clinitex.transcription.ui.components.PillVariant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lot 6b, top of the Réunions and Vocaux lists: shown only while summaries
 * wait for the network or have failed. "Forcer la reconnexion" sends every
 * waiting or failed summary again right away (both tabs).
 */
@Composable
fun SummaryRecoveryBanner(sessions: List<RecordingSession>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    val label = SessionStatus.pendingSummariesLabel(sessions)

    LaunchedEffect(message) {
        if (message != null) {
            delay(6_000)
            message = null
        }
    }

    if (label == null && message == null) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (label != null) {
            val shape = RoundedCornerShape(MuesliCorners.row)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(ClinitexPalette.Paper)
                    .border(1.dp, ClinitexPalette.Line, shape)
                    .padding(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = ClinitexPalette.Blue,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        label,
                        style = ClinitexType.body(14.sp, FontWeight.Bold, 19.sp),
                        color = ClinitexPalette.Ink,
                    )
                }
                PillButton(
                    text = "Forcer la reconnexion",
                    onClick = {
                        busy = true
                        scope.launch {
                            val relaunched = SummaryRecoveryWorker.forceAll(context)
                            message = forceMessage(isOnline(context), relaunched)
                            busy = false
                        }
                    },
                    variant = PillVariant.OUTLINE,
                    icon = Icons.Default.Refresh,
                    loading = busy,
                    height = 44.dp,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        message?.let { (text, tone) -> NoticeBox(text, tone) }
    }
}

private fun forceMessage(online: Boolean, relaunched: Int): Pair<String, NoticeTone> = when {
    !online -> "Pas de réseau : les synthèses partiront dès que le téléphone sera reconnecté." to NoticeTone.WARNING
    relaunched == 0 -> "Envoi déjà en cours." to NoticeTone.INFO
    relaunched == 1 -> "Envoi relancé pour 1 synthèse." to NoticeTone.SUCCESS
    else -> "Envoi relancé pour $relaunched synthèses." to NoticeTone.SUCCESS
}

private fun isOnline(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
