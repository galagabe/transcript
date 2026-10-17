package fr.clinitex.transcription.ui.launch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.R
import fr.clinitex.transcription.engine.SherpaRecognizerHolder
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.ui.components.brandUpper

/**
 * Launch warm-up screen shown while the Parakeet model primes for inference:
 * white page, app icon + CLINDY wordmark, status line with a flat blue pulse,
 * official Clinitex logo and version at the bottom. The caller dismisses it
 * when the model is warm or after the 5-second display cap.
 */
@Composable
fun LaunchWarmupScreen(
    warmState: SherpaRecognizerHolder.WarmState,
    modelName: String,
    versionText: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.Paper)
            .systemBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-18).dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_muesli_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            Text(
                "Clindy".brandUpper(),
                style = ClinitexType.display(34.sp, 34.sp),
                color = ClinitexPalette.Blue,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (warmState == SherpaRecognizerHolder.WarmState.FAILED) {
                    Icon(
                        Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = ClinitexPalette.Muted,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ClinitexPalette.Blue
                    )
                }
                Text(
                    text = when (warmState) {
                        SherpaRecognizerHolder.WarmState.READY -> "Moteur de transcription prêt"
                        SherpaRecognizerHolder.WarmState.FAILED -> "Préchargement interrompu"
                        else -> "Préparation du moteur de transcription…"
                    },
                    style = ClinitexType.body(14.sp, FontWeight.Medium, 20.sp),
                    color = ClinitexPalette.InkSoft,
                    textAlign = TextAlign.Center
                )
            }

            WarmupPulseLine(
                isAnimating = warmState == SherpaRecognizerHolder.WarmState.WARMING,
                modifier = Modifier
                    .width(176.dp)
                    .height(4.dp)
            )

            Text(modelName, style = ClinitexType.body(12.sp), color = ClinitexPalette.Muted)

            Spacer(Modifier.height(20.dp))
            // Official logo, used as is (light background only).
            Image(
                painter = painterResource(id = R.drawable.logo_clinitex),
                contentDescription = "Clinitex, Engagés au sens propre",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2500f / 987f),
            )
            Text(versionText, style = ClinitexType.body(12.sp), color = ClinitexPalette.Muted)
        }
    }
}

/** Flat blue segment sliding on a line-colored capsule track (no gradient). */
@Composable
private fun WarmupPulseLine(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "warmup-pulse")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "warmup-pulse-fraction"
    )

    BoxWithConstraints(modifier = modifier.clip(CircleShape)) {
        val trackWidth = maxWidth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClinitexPalette.Line)
        )
        Box(
            modifier = Modifier
                .width(trackWidth * 0.36f)
                .fillMaxHeight()
                .offset(x = if (isAnimating) trackWidth * 0.64f * fraction else 0.dp)
                .background(ClinitexPalette.Blue, shape = CircleShape)
        )
    }
}
