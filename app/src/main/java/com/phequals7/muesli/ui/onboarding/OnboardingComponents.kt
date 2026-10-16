package com.phequals7.muesli.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.R
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme

/** Ordered onboarding steps: welcome → permissions → model download → done. */
enum class OnboardingStep(val title: String) {
    WELCOME("Bienvenue"),
    PERMISSIONS("Autorisations"),
    MODEL("Modèle de transcription"),
    DONE("C’est prêt"),
}

/** Shared page chrome: progress capsule, title, subtitle, content, footer. */
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
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MuesliTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDeep)
            .padding(MuesliSpacing.s24),
    ) {
        // Progress capsule (iOS progress header parity)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / stepCount },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = colors.accent,
                trackColor = colors.surfacePrimary,
            )
            Text(
                "${stepIndex + 1} sur $stepCount",
                color = colors.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(MuesliSpacing.s24))

        Text(
            step.title,
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Text(
            subtitle,
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = MuesliSpacing.s4),
        )

        Spacer(modifier = Modifier.height(MuesliSpacing.s20))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
            content = content,
        )

        if (skipLabel != null && onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(skipLabel, color = colors.textSecondary, fontSize = 14.sp)
            }
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                disabledContainerColor = colors.accent.copy(alpha = 0.3f),
            ),
            shape = RoundedCornerShape(MuesliCorners.medium),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(primaryLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Retour", color = colors.textTertiary, fontSize = 13.sp)
            }
        }
    }
}

/** Brand header reused on the welcome page. */
@Composable
internal fun OnboardingBrandHeader() {
    val colors = MuesliTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_muesli_logo),
            contentDescription = "Logo de l’application",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(MuesliCorners.large)),
        )
        Spacer(modifier = Modifier.height(MuesliSpacing.s8))
        Text(
            "Clindy",
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Text(
            "Comptes rendus de réunion, transcrits sur votre téléphone.",
            color = colors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

/** Card container matching the settings cards. */
@Composable
internal fun OnboardingCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MuesliTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MuesliCorners.medium))
            .background(colors.backgroundRaised)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(MuesliCorners.medium))
            .padding(MuesliSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
        content = content,
    )
}

/** Selectable option row. */
@Composable
internal fun OnboardingChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MuesliTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MuesliCorners.small))
            .background(if (selected) colors.accentSubtle else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                1.dp,
                if (selected) colors.accent else colors.surfaceBorder,
                RoundedCornerShape(MuesliCorners.small),
            )
            .clickable { onClick() }
            .padding(MuesliSpacing.s12),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) colors.accent else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

/** Status icon tile (granted = green check). */
@Composable
internal fun OnboardingStatusTile(done: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = MuesliTheme.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(MuesliCorners.small))
            .background(if (done) colors.syncGreenSubtle else colors.surfacePrimary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (done) Icons.Default.Check else icon,
            contentDescription = null,
            tint = if (done) colors.syncGreen else colors.textPrimary,
        )
    }
}
