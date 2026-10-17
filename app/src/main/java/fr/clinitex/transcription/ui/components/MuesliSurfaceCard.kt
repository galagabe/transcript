package fr.clinitex.transcription.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.MuesliCorners

/** Brand card container (white, 1dp line border, 18dp radius). See also [BrandCard]. */
@Composable
fun MuesliSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(MuesliCorners.card)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = ClinitexPalette.Blue.copy(alpha = 0.10f),
                spotColor = ClinitexPalette.Blue.copy(alpha = 0.10f),
            )
            .clip(shape)
            .background(ClinitexPalette.Paper)
            .border(1.dp, ClinitexPalette.Line, shape),
    ) {
        content()
    }
}
