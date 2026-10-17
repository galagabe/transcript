package fr.clinitex.transcription.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Material3 mapping of the Clinitex palette (see MuesliColors.kt).
// Light only: no dark scheme, no dynamic (wallpaper) color, no accent choice.

private val ClinitexMaterialScheme =
  lightColorScheme(
    primary = ClinitexPalette.Blue,
    onPrimary = ClinitexPalette.Paper,
    primaryContainer = ClinitexPalette.PaperTint,
    onPrimaryContainer = ClinitexPalette.Ink,
    secondary = ClinitexPalette.Blue,
    onSecondary = ClinitexPalette.Paper,
    secondaryContainer = ClinitexPalette.Mint,
    onSecondaryContainer = ClinitexPalette.Ink,
    tertiary = ClinitexPalette.Sky,
    onTertiary = ClinitexPalette.Ink,
    background = ClinitexPalette.PaperTint,
    onBackground = ClinitexPalette.Ink,
    surface = ClinitexPalette.Paper,
    onSurface = ClinitexPalette.Ink,
    surfaceVariant = ClinitexPalette.PaperTint,
    onSurfaceVariant = ClinitexPalette.InkSoft,
    surfaceContainer = ClinitexPalette.Paper,
    surfaceContainerLow = ClinitexPalette.Paper,
    surfaceContainerHigh = ClinitexPalette.Paper,
    surfaceContainerHighest = ClinitexPalette.PaperTint,
    outline = ClinitexPalette.Line,
    outlineVariant = ClinitexPalette.Line,
    error = ClinitexPalette.Peach,
    onError = ClinitexPalette.Ink,
    errorContainer = ClinitexPalette.Peach,
    onErrorContainer = ClinitexPalette.Ink,
  )

/** App theme (name kept from the Muesli port; renaming is a later lot). */
@Composable
fun MuesliTheme(
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalMuesliColors provides MuesliLightColors) {
    MaterialTheme(
      colorScheme = ClinitexMaterialScheme,
      typography = Typography,
      content = content,
    )
  }
}
