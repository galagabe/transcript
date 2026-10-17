package com.phequals7.muesli.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.theme.ClinitexPalette
import com.phequals7.muesli.theme.ClinitexType
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.ui.components.BrandCard
import com.phequals7.muesli.ui.components.DisplayTitle
import com.phequals7.muesli.ui.components.Eyebrow
import com.phequals7.muesli.ui.components.PillButton
import com.phequals7.muesli.ui.components.PillVariant
import com.phequals7.muesli.ui.components.ScreenBackground

/** Ordered onboarding steps: welcome → permissions → model download → done. */
enum class OnboardingStep(val title: String) {
    WELCOME("Bienvenue"),
    PERMISSIONS("Autorisations"),
    MODEL("Modèle de transcription"),
    DONE("C’est prêt"),
}

/** Page chrome for steps 2–4: progress, eyebrow, uppercase title, content, pill footer. */
@Composable
internal fun OnboardingPage(
    step: OnboardingStep,
    stepIndex: Int,
    stepCount: Int,
    subtitle: String,
    onBack: (() -> Unit)?,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenBackground(ClinitexPalette.Paper)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.Paper)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
    ) {
        LinearProgressIndicator(
            progress = { (stepIndex + 1).toFloat() / stepCount },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = ClinitexPalette.Blue,
            trackColor = ClinitexPalette.Line,
            drawStopIndicator = {},
        )

        Spacer(modifier = Modifier.height(28.dp))
        Eyebrow("Étape ${stepIndex + 1} sur $stepCount", withBar = true, tracking = 0.14f)
        Spacer(modifier = Modifier.height(10.dp))
        DisplayTitle(step.title, size = 30.sp, lineHeight = 31.sp)
        Text(
            subtitle,
            style = ClinitexType.body(15.sp, FontWeight.Normal, 23.sp),
            color = ClinitexPalette.InkSoft,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )

        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            primaryLabel,
            onClick = onPrimary,
            enabled = primaryEnabled,
            height = 54.dp,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onBack != null) {
            Spacer(modifier = Modifier.height(10.dp))
            PillButton(
                "Retour",
                onClick = onBack,
                variant = PillVariant.OUTLINE,
                height = 46.dp,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Card container matching the settings cards. */
@Composable
internal fun OnboardingCard(content: @Composable ColumnScope.() -> Unit) {
    BrandCard(padding = PaddingValues(16.dp), spacing = 12.dp, content = content)
}

/** 40dp icon tile: paper-tint with blue icon, mint with ink check once done. */
@Composable
internal fun OnboardingStatusTile(done: Boolean, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(MuesliCorners.tile))
            .background(if (done) ClinitexPalette.Mint else ClinitexPalette.PaperTint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (done) Icons.Default.Check else icon,
            contentDescription = null,
            tint = if (done) ClinitexPalette.Ink else ClinitexPalette.Blue,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Feature line of the welcome screen: soft icon tile + sentence. */
@Composable
internal fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingStatusTile(done = false, icon = icon)
        Text(text, style = ClinitexType.body(15.sp, FontWeight.Normal, 21.sp), color = ClinitexPalette.Ink)
    }
}
