package com.phequals7.muesli.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliTheme

@Composable
fun MuesliSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MuesliTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.backgroundRaised),
        shape = RoundedCornerShape(MuesliCorners.large),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(MuesliCorners.large)),
        content = { content() }
    )
}
