package com.phequals7.muesli.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.R

/**
 * Clinitex typography (brandbook 2024):
 * - Display titles and eyebrows: AZO Sans Uber, weight 900, ALWAYS uppercase
 *   (Compose has no text-transform: callers pass `.uppercase(Locale.FRENCH)`).
 * - Body: Sofia Pro Soft 400 / 500 / 700, sentence case.
 */
val AzoSansUber = FontFamily(
  Font(R.font.azo_sans_uber_black, FontWeight.Black),
)

val SofiaProSoft = FontFamily(
  Font(R.font.sofia_pro_soft_regular, FontWeight.Normal),
  Font(R.font.sofia_pro_soft_medium, FontWeight.Medium),
  Font(R.font.sofia_pro_soft_bold, FontWeight.Bold),
)

/** Brand text styles used directly by the screens. */
object ClinitexType {
  /** Display title (AZO 900). Text must be uppercased by the caller. */
  fun display(size: TextUnit, lineHeight: TextUnit = size * 1.04f): TextStyle = TextStyle(
    fontFamily = AzoSansUber,
    fontWeight = FontWeight.Black,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = (-0.01).em,
  )

  /** Small uppercase label, often preceded by a 22×2dp blue bar. */
  fun eyebrow(size: TextUnit = 12.sp, tracking: Float = 0.12f): TextStyle = TextStyle(
    fontFamily = AzoSansUber,
    fontWeight = FontWeight.Black,
    fontSize = size,
    lineHeight = size * 1.3f,
    letterSpacing = tracking.em,
  )

  fun body(size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = size * 1.5f): TextStyle =
    TextStyle(
      fontFamily = SofiaProSoft,
      fontWeight = weight,
      fontSize = size,
      lineHeight = lineHeight,
    )
}

val Typography =
  Typography(
    displayLarge = ClinitexType.display(44.sp, 42.sp),
    displayMedium = ClinitexType.display(34.sp, 33.sp),
    displaySmall = ClinitexType.display(28.sp, 30.sp),
    headlineLarge = ClinitexType.display(28.sp, 30.sp),
    headlineMedium = ClinitexType.display(24.sp, 26.sp),
    headlineSmall = ClinitexType.display(20.sp, 22.sp),
    titleLarge = ClinitexType.body(20.sp, FontWeight.Bold, 26.sp),
    titleMedium = ClinitexType.body(16.sp, FontWeight.Bold, 22.sp),
    titleSmall = ClinitexType.body(14.sp, FontWeight.Bold, 20.sp),
    bodyLarge = ClinitexType.body(16.sp, FontWeight.Normal, 24.sp),
    bodyMedium = ClinitexType.body(15.sp, FontWeight.Normal, 22.sp),
    bodySmall = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp),
    labelLarge = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp),
    labelMedium = ClinitexType.body(13.sp, FontWeight.Medium, 18.sp),
    labelSmall = ClinitexType.body(12.sp, FontWeight.Medium, 16.sp),
  )
