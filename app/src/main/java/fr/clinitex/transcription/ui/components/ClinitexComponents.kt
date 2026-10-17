package fr.clinitex.transcription.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.theme.AppChrome
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.theme.MuesliCorners
import fr.clinitex.transcription.ui.dashboard.BadgeTone
import fr.clinitex.transcription.ui.dashboard.SessionBadge
import java.util.Locale

/*
 * Clinitex UI building blocks (brandbook 2024): pill buttons, white cards with
 * a 1dp line border, AZO uppercase titles and eyebrows, status badges in the
 * complementary colors (always with ink text), 12dp inputs. No gradients.
 */

/** Declares the background of the current screen (painted behind the system bars too). */
@Composable
fun ScreenBackground(color: Color) {
    SideEffect { AppChrome.background = color }
}

fun String.brandUpper(): String = uppercase(Locale.FRENCH)

/** Short uppercase AZO label, optionally preceded by the 22×2dp blue bar. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ClinitexPalette.Blue,
    withBar: Boolean = false,
    size: TextUnit = 12.sp,
    tracking: Float = 0.12f,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (withBar) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(2.dp)
                    .background(color)
            )
        }
        Text(text.brandUpper(), style = ClinitexType.eyebrow(size, tracking), color = color)
    }
}

/** AZO 900 uppercase title. */
@Composable
fun DisplayTitle(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 34.sp,
    lineHeight: TextUnit = size * 1.0f,
    color: Color = ClinitexPalette.Blue,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text.brandUpper(),
        modifier = modifier,
        style = ClinitexType.display(size, lineHeight),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Tab header: "— CLINDY" eyebrow + big uppercase title (+ optional intro line). */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, intro: String? = null) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow("Clindy", withBar = true, tracking = 0.14f)
        DisplayTitle(title, size = 34.sp, lineHeight = 33.sp)
        if (intro != null) {
            Text(
                intro,
                style = ClinitexType.body(14.sp),
                color = ClinitexPalette.InkSoft,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

enum class PillVariant { PRIMARY, OUTLINE, PEACH, ON_BLUE_OUTLINE, ON_BLUE_SOLID }

/** Fully rounded button (the only button shape allowed by the brand). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    fontSize: TextUnit = 15.sp,
    loading: Boolean = false,
) {
    val (container, content, border) = when (variant) {
        PillVariant.PRIMARY -> Triple(ClinitexPalette.Blue, ClinitexPalette.Paper, null)
        PillVariant.OUTLINE -> Triple(ClinitexPalette.Paper, ClinitexPalette.Blue, ClinitexPalette.Blue)
        PillVariant.PEACH -> Triple(ClinitexPalette.Peach, ClinitexPalette.Ink, null)
        PillVariant.ON_BLUE_OUTLINE -> Triple(Color.Transparent, ClinitexPalette.Paper, ClinitexPalette.Paper)
        PillVariant.ON_BLUE_SOLID -> Triple(ClinitexPalette.Paper, ClinitexPalette.Blue, null)
    }
    Row(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(container)
            .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = ClinitexType.body(fontSize, if (variant == PillVariant.ON_BLUE_OUTLINE) FontWeight.Medium else FontWeight.Bold, fontSize * 1.3f),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small round icon-only button (top bars). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = ClinitexPalette.Blue,
    iconSize: Dp = 22.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** White card, 1dp line border, 18dp radius, very light blue-tinted shadow. */
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    radius: Dp = MuesliCorners.card,
    padding: PaddingValues = PaddingValues(18.dp),
    spacing: Dp = 12.dp,
    shadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (shadow) Modifier.shadow(
                    elevation = 2.dp,
                    shape = shape,
                    ambientColor = ClinitexPalette.Blue.copy(alpha = 0.10f),
                    spotColor = ClinitexPalette.Blue.copy(alpha = 0.10f),
                ) else Modifier
            )
            .clip(shape)
            .background(ClinitexPalette.Paper)
            .border(1.dp, ClinitexPalette.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Status pill: mint "Prêt", blue-outline "En attente", peach "Échec", neutral. */
@Composable
fun StatusBadge(
    label: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    withCheck: Boolean = false,
    loading: Boolean = false,
) {
    val (fill, text, border) = when (tone) {
        BadgeTone.READY -> Triple(ClinitexPalette.Mint, ClinitexPalette.Ink, null)
        BadgeTone.PENDING -> Triple(Color.Transparent, ClinitexPalette.Blue, ClinitexPalette.Blue)
        BadgeTone.FAILED -> Triple(ClinitexPalette.Peach, ClinitexPalette.Ink, null)
        BadgeTone.NEUTRAL -> Triple(ClinitexPalette.PaperTint, ClinitexPalette.InkSoft, ClinitexPalette.Line)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = text)
        } else if (withCheck) {
            Icon(Icons.Default.Check, contentDescription = null, tint = text, modifier = Modifier.size(14.dp))
        }
        Text(label, style = ClinitexType.body(12.sp, FontWeight.Bold, 16.sp), color = text, maxLines = 1)
    }
}

@Composable
fun StatusBadge(badge: SessionBadge, modifier: Modifier = Modifier) =
    StatusBadge(badge.label, badge.tone, modifier)

enum class TileStyle { FILLED, SOFT, DASHED }

/** 42dp icon tile used in list rows. DASHED = waiting for network. */
@Composable
fun IconTile(icon: ImageVector, style: TileStyle, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val shape = RoundedCornerShape(MuesliCorners.tile)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (style == TileStyle.FILLED) ClinitexPalette.Blue else ClinitexPalette.PaperTint)
            .then(
                if (style == TileStyle.DASHED) Modifier.drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = ClinitexPalette.Blue,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                        cornerRadius = CornerRadius(MuesliCorners.tile.toPx() - stroke / 2),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                } else Modifier
            ),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (style == TileStyle.FILLED) ClinitexPalette.Paper else ClinitexPalette.Blue,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Pill segmented control ("Compte rendu / Transcription / Notes"). */
@Composable
fun SegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(ClinitexPalette.PaperTint)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) ClinitexPalette.Blue else Color.Transparent)
                    .clickable { onSelect(index) },
            ) {
                Text(
                    label,
                    style = ClinitexType.body(14.sp, if (selected) FontWeight.Bold else FontWeight.Medium, 18.sp),
                    color = if (selected) ClinitexPalette.Paper else ClinitexPalette.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

enum class NoticeTone { SUCCESS, WARNING, DANGER, INFO }

/** Inline message on a complementary fill with ink text (never red). */
@Composable
fun NoticeBox(text: String, tone: NoticeTone, modifier: Modifier = Modifier) {
    val fill = when (tone) {
        NoticeTone.SUCCESS -> ClinitexPalette.Mint
        NoticeTone.WARNING -> ClinitexPalette.Butter
        NoticeTone.DANGER -> ClinitexPalette.Peach
        NoticeTone.INFO -> ClinitexPalette.Sky
    }
    Text(
        text,
        style = ClinitexType.body(13.sp, FontWeight.Medium, 19.sp),
        color = ClinitexPalette.Ink,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MuesliCorners.tile))
            .background(fill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * Brand text input: white, 1dp line border (blue when focused), 12dp radius
 * by default (pass [CircleShape] for the search pill), 44dp high when single-line.
 */
@Composable
fun BrandInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(MuesliCorners.input),
    leadingIcon: ImageVector? = null,
    minHeight: Dp = 44.dp,
    background: Color = ClinitexPalette.Paper,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = ClinitexType.body(15.sp, FontWeight.Normal, 21.sp).copy(color = ClinitexPalette.Ink)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(ClinitexPalette.Blue),
        interactionSource = interaction,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(shape)
                    .background(background)
                    .border(1.dp, if (focused) ClinitexPalette.Blue else ClinitexPalette.Line, shape)
                    .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 12.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = ClinitexPalette.Muted, modifier = Modifier.size(18.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = textStyle, color = ClinitexPalette.Muted)
                    }
                    inner()
                }
            }
        },
    )
}

/** Small caption above a field ("Titre"). */
@Composable
fun FieldLabel(text: String) {
    Text(text, style = ClinitexType.body(13.sp, FontWeight.Normal, 18.sp), color = ClinitexPalette.Muted)
}

/** Brand switch colors: blue track when on, line track when off. */
@Composable
fun brandSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = ClinitexPalette.Paper,
    checkedTrackColor = ClinitexPalette.Blue,
    checkedBorderColor = ClinitexPalette.Blue,
    uncheckedThumbColor = ClinitexPalette.Muted,
    uncheckedTrackColor = ClinitexPalette.Line,
    uncheckedBorderColor = ClinitexPalette.Line,
)

/** Confirmation dialog with pill buttons. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Annuler",
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ClinitexPalette.Paper,
        shape = RoundedCornerShape(MuesliCorners.card),
        title = { Text(title, style = ClinitexType.body(18.sp, FontWeight.Bold, 24.sp), color = ClinitexPalette.Ink) },
        text = { Text(text, style = ClinitexType.body(14.sp), color = ClinitexPalette.InkSoft) },
        confirmButton = {
            PillButton(
                confirmLabel,
                onClick = onConfirm,
                variant = if (destructive) PillVariant.PEACH else PillVariant.PRIMARY,
                height = 44.dp,
                fontSize = 14.sp,
            )
        },
        dismissButton = {
            PillButton(dismissLabel, onClick = onDismiss, variant = PillVariant.OUTLINE, height = 44.dp, fontSize = 14.sp)
        },
    )
}

/** Thin top border + padded row for the sticky bottom action bars of detail screens. */
@Composable
fun BottomActionBar(content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ClinitexPalette.Paper)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ClinitexPalette.Line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Empty-state card body. */
@Composable
fun EmptyState(icon: ImageVector, title: String, detail: String?) {
    BrandCard(spacing = 8.dp, shadow = false) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(icon, TileStyle.SOFT)
            Text(title, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink, textAlign = TextAlign.Center)
            if (detail != null) {
                Text(detail, style = ClinitexType.body(13.sp), color = ClinitexPalette.Muted, textAlign = TextAlign.Center)
            }
        }
    }
}
