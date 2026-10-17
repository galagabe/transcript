package fr.clinitex.transcription.ui.dashboard.meetings

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Inline playback pill for a retained WAV (meetings and voice notes):
 * play/pause, a thin progress track (tap or drag to seek), "0:23 / 1:12".
 */
@Composable
fun MeetingAudioPlayer(audioFile: File, modifier: Modifier = Modifier) {
    if (!audioFile.exists()) {
        AudioUnavailableRow(modifier)
        return
    }

    // prepare() is synchronous but fine for a local WAV; failure → graceful fallback.
    val player = remember(audioFile) {
        try {
            MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
            }
        } catch (_: Exception) {
            null
        }
    }
    if (player == null) {
        AudioUnavailableRow(modifier)
        return
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableIntStateOf(1) }
    val durationMs = remember(player) { player.duration.coerceAtLeast(0) }

    DisposableEffect(player) {
        player.setOnCompletionListener {
            isPlaying = false
            positionMs = 0
            dragFraction = null
            try {
                player.pause()
                player.seekTo(0)
            } catch (_: Exception) {
            }
        }
        onDispose {
            try {
                if (player.isPlaying) player.pause()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    // Ticker: poll position while playing so the track advances smoothly.
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            val pos = try {
                player.currentPosition
            } catch (_: Exception) {
                break
            }
            positionMs = pos
            delay(250)
        }
    }

    fun togglePlayPause() {
        try {
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
                positionMs = player.currentPosition
            }
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun seekToFraction(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * durationMs).toInt()
        try {
            player.seekTo(target)
            positionMs = target
        } catch (_: Exception) {
        }
    }

    val shownMs = dragFraction?.let { (it * durationMs).toInt() } ?: positionMs.coerceIn(0, durationMs)
    val fraction = if (durationMs > 0) shownMs.toFloat() / durationMs else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(ClinitexPalette.PaperTint)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ClinitexPalette.Blue)
                .clickable { togglePlayPause() },
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Mettre en pause" else "Écouter l’enregistrement",
                tint = ClinitexPalette.Paper,
                modifier = Modifier.size(20.dp),
            )
        }

        // Progress track, 4dp high inside a taller touch area.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
                .pointerInput(durationMs) {
                    detectTapGestures { offset -> seekToFraction(offset.x / trackWidthPx) }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / trackWidthPx).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { seekToFraction(it) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(ClinitexPalette.Line)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(ClinitexPalette.Blue)
            )
        }

        Text(
            "${formatMillis(shownMs)} / ${formatMillis(durationMs)}",
            style = ClinitexType.body(12.sp).copy(fontFeatureSettings = "tnum"),
            color = ClinitexPalette.Muted,
        )
    }
}

@Composable
private fun AudioUnavailableRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(ClinitexPalette.PaperTint)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ClinitexPalette.Muted, modifier = Modifier.size(16.dp))
        Text("Audio indisponible", style = ClinitexType.body(13.sp), color = ClinitexPalette.Muted)
    }
}

private fun formatMillis(ms: Int): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
