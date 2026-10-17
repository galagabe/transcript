package fr.clinitex.transcription.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clinitex brand palette (brandbook 2024). Bleu Clinitex dominates; the four
 * complementary colors are only ever combined with the blue, never with each
 * other. Status colors: success = mint, warning = butter, danger = peach,
 * info = sky, always with ink text on top. No gradients, no dark theme.
 */
object ClinitexPalette {
  val Blue = Color(0xFF164194)
  val Mint = Color(0xFFC9E5D8)
  val Sky = Color(0xFF7CA0D4)
  val Butter = Color(0xFFFEF7BD)
  val Peach = Color(0xFFF5A793)

  val Paper = Color(0xFFFFFFFF)
  val PaperTint = Color(0xFFF6F8FC)
  val Line = Color(0xFFE4EBF4)
  val Muted = Color(0xFF6B7A95)
  val InkSoft = Color(0xFF3A5082)
  val Ink = Color(0xFF0E2A5C)
}

/**
 * App color roles. Field names are kept from the Muesli port (renaming is a
 * later lot) but every role now maps onto the Clinitex palette.
 */
@Immutable
data class MuesliColors(
  val backgroundDeep: Color,
  val backgroundBase: Color,
  val backgroundRaised: Color,
  val backgroundHover: Color,
  val surfacePrimary: Color,
  val surfaceSelected: Color,
  val surfaceBorder: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textTertiary: Color,
  val accent: Color,
  val brandBlue: Color,
  val brandBlueSubtle: Color,
  val syncGreen: Color,
  val syncGreenSubtle: Color,
  val recording: Color,
  val transcribing: Color,
  val success: Color,
  val destructive: Color,
) {
  val accentSubtle: Color get() = brandBlueSubtle
  val destructiveSubtle: Color get() = destructive
}

/** The only color set: Clindy is light-only (the recording screen is the full-blue exception). */
val MuesliLightColors =
  MuesliColors(
    backgroundDeep = ClinitexPalette.PaperTint,
    backgroundBase = ClinitexPalette.Paper,
    backgroundRaised = ClinitexPalette.Paper,
    backgroundHover = ClinitexPalette.PaperTint,
    surfacePrimary = ClinitexPalette.PaperTint,
    surfaceSelected = ClinitexPalette.Line,
    surfaceBorder = ClinitexPalette.Line,
    textPrimary = ClinitexPalette.Ink,
    textSecondary = ClinitexPalette.InkSoft,
    textTertiary = ClinitexPalette.Muted,
    accent = ClinitexPalette.Blue,
    brandBlue = ClinitexPalette.Blue,
    brandBlueSubtle = ClinitexPalette.PaperTint,
    syncGreen = ClinitexPalette.Mint,
    syncGreenSubtle = ClinitexPalette.Mint,
    recording = ClinitexPalette.Peach,
    transcribing = ClinitexPalette.Sky,
    success = ClinitexPalette.Mint,
    destructive = ClinitexPalette.Peach,
  )

/** Spacing scale. */
object MuesliSpacing {
  val s4: Dp = 4.dp
  val s8: Dp = 8.dp
  val s12: Dp = 12.dp
  val s16: Dp = 16.dp
  val s20: Dp = 20.dp
  val s24: Dp = 24.dp
  val s32: Dp = 32.dp
}

/** Corner radii (Clinitex: inputs 12dp, cards 14–18dp, buttons fully rounded). */
object MuesliCorners {
  val small: Dp = 8.dp
  val medium: Dp = 12.dp
  val large: Dp = 16.dp
  val xl: Dp = 22.dp

  /** Inputs, pickers. */
  val input: Dp = 12.dp

  /** Cards and list rows. */
  val card: Dp = 18.dp
  val row: Dp = 16.dp

  /** Icon tiles. */
  val tile: Dp = 14.dp

  /** Floating navigation (pill). */
  val navContainer: Dp = 999.dp
  val navSelection: Dp = 999.dp
}

val LocalMuesliColors = staticCompositionLocalOf { MuesliLightColors }

/** Access brand tokens via `MuesliTheme.colors` from any @Composable. */
object MuesliTheme {
  val colors: MuesliColors
    @Composable get() = LocalMuesliColors.current
}

/**
 * Background of the screen currently shown, read by MainActivity to paint the
 * area behind the system bars and pick dark or light status bar icons.
 * Screens set it through [fr.clinitex.transcription.ui.components.ScreenBackground].
 */
object AppChrome {
  var background by mutableStateOf(ClinitexPalette.PaperTint)
}
