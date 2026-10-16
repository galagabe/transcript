package com.phequals7.muesli.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModels
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import kotlinx.coroutines.launch

/**
 * Multi-step onboarding: Welcome (optional name) → Permissions (microphone,
 * notifications) → Speech model download (Parakeet v3) → Done.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { SharedStore(context) }

    val steps = OnboardingStep.entries
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps[stepIndex]

    var profileName by remember { mutableStateOf(store.userProfileName) }

    fun finish() {
        store.userProfileName = profileName.trim()
        store.isOnboardingCompleted = true
        onFinished()
    }

    val onNext: () -> Unit = { if (stepIndex < steps.lastIndex) stepIndex++ else finish() }
    val onBack: (() -> Unit)? = if (stepIndex > 0) ({ stepIndex-- }) else null

    Box(modifier = modifier) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                stepIndex = stepIndex, stepCount = steps.size, onBack = onBack,
                name = profileName, onNameChange = { profileName = it },
                onNext = onNext,
            )
            OnboardingStep.PERMISSIONS -> PermissionsStep(
                stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
            OnboardingStep.MODEL -> ModelStep(
                stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
            OnboardingStep.DONE -> DoneStep(
                stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
        }
    }
}

// ── 1. Welcome ──────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?,
    name: String, onNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val colors = MuesliTheme.colors
    OnboardingPage(
        step = OnboardingStep.WELCOME, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Avec Clindy, enregistrez vos rendez-vous et réunions : la transcription se fait sur le téléphone, le compte rendu est généré dès que vous avez du réseau.",
        onBack = onBack,
        primaryLabel = "Continuer",
        primaryEnabled = true,
        onPrimary = onNext,
    ) {
        OnboardingBrandHeader()
        OnboardingCard {
            Text("Votre prénom (facultatif)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("Ex. : Camille", color = colors.textTertiary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfacePrimary,
                    unfocusedContainerColor = colors.surfacePrimary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(MuesliCorners.medium),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── 2. Permissions ──────────────────────────────────────────────────────

@Composable
private fun PermissionsStep(
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    val context = LocalContext.current
    fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // Notifications need a runtime permission only from Android 13.
    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var micGranted by remember { mutableStateOf(isGranted(Manifest.permission.RECORD_AUDIO)) }
    var notificationsGranted by remember {
        mutableStateOf(!needsNotificationPermission || isGranted(Manifest.permission.POST_NOTIFICATIONS))
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { micGranted = it }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsGranted = it }

    OnboardingPage(
        step = OnboardingStep.PERMISSIONS, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Clindy a besoin du micro pour enregistrer. La transcription se fait sur le téléphone : l’audio ne quitte jamais l’appareil.",
        onBack = onBack,
        primaryLabel = "Continuer",
        primaryEnabled = micGranted,
        onPrimary = onNext,
    ) {
        PermissionCard(
            icon = Icons.Default.Mic,
            title = "Accès au micro",
            status = if (micGranted) "Autorisé" else "Obligatoire pour enregistrer les réunions",
            granted = micGranted,
            onGrant = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )
        if (needsNotificationPermission) {
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                status = if (notificationsGranted) "Autorisées" else "Recommandées : suivi et arrêt de l’enregistrement en cours",
                granted = notificationsGranted,
                onGrant = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    status: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val colors = MuesliTheme.colors
    OnboardingCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OnboardingStatusTile(done = granted, icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    status,
                    color = if (granted) colors.syncGreen else colors.textSecondary,
                    fontSize = 11.sp,
                )
            }
            if (!granted) {
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(MuesliCorners.medium),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Autoriser", fontSize = 12.sp)
                }
            }
        }
    }
}

// ── 3. Speech model ─────────────────────────────────────────────────────

@Composable
private fun ModelStep(
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    val scope = rememberCoroutineScope()

    // Single-model catalog: Parakeet v3 (multilingual, required for French).
    val model = SpeechModels.PARAKEET_V3
    val manager = remember { ModelManager(context.applicationContext, model) }
    var downloaded by remember { mutableStateOf(manager.isDownloaded()) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ModelManager.DownloadProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    OnboardingPage(
        step = OnboardingStep.MODEL, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Le modèle de reconnaissance vocale fonctionne sans réseau, directement sur le téléphone. Il se télécharge une seule fois (environ ${manager.formatBytes(model.totalSizeBytes)}) : privilégiez le Wi-Fi.",
        onBack = onBack,
        primaryLabel = "Continuer",
        primaryEnabled = downloaded,
        onPrimary = onNext,
    ) {
        OnboardingCard {
            Text(model.displayName, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(model.capabilityLabel, color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(model.detail, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }

        OnboardingCard {
            Text(
                when {
                    downloaded -> "Le modèle est prêt"
                    downloading && progress?.extracting == true -> "Extraction…"
                    downloading && progress != null ->
                        "${(progress!!.fraction * 100).toInt()} % · ${manager.formatBytes(progress!!.totalBytesDone)} sur ${manager.formatBytes(progress!!.totalBytes)}"
                    else -> "Téléchargement unique nécessaire"
                },
                color = if (downloaded) colors.syncGreen else colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (downloading && progress != null) {
                LinearProgressIndicator(
                    progress = { progress!!.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.accent,
                    trackColor = colors.surfacePrimary,
                )
            }
            error?.let { Text(it, color = colors.destructive, fontSize = 11.sp) }
            if (!downloaded) {
                if (downloading) {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        downloading = false
                        progress = null
                    }) {
                        Text("Annuler le téléchargement", color = colors.textSecondary)
                    }
                } else {
                    Button(
                        onClick = {
                            error = null
                            downloading = true
                            downloadJob = scope.launch {
                                try {
                                    manager.download { p -> progress = p }
                                    downloaded = true
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    error = "Échec du téléchargement : ${t.message}"
                                } finally {
                                    downloading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (manager.downloadedBytes() > 0) "Reprendre le téléchargement" else "Télécharger le modèle",
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── 4. Done ─────────────────────────────────────────────────────────────

@Composable
private fun DoneStep(
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    OnboardingPage(
        step = OnboardingStep.DONE, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Vous pouvez enregistrer votre première réunion.",
        onBack = onBack,
        primaryLabel = "Commencer",
        primaryEnabled = true,
        onPrimary = onNext,
    ) {
        DoneRow(
            icon = Icons.Default.Mic,
            title = "Enregistrez",
            detail = "Informez les participants, puis lancez l’enregistrement depuis l’onglet Réunions.",
        )
        DoneRow(
            icon = Icons.Default.CloudDownload,
            title = "Transcription locale",
            detail = "Le texte et les intervenants sont produits sur le téléphone, même sans réseau.",
        )
        DoneRow(
            icon = Icons.Default.AutoAwesome,
            title = "Compte rendu automatique",
            detail = "La synthèse est générée dès que le téléphone retrouve du réseau.",
        )
    }
}

@Composable
private fun DoneRow(icon: ImageVector, title: String, detail: String) {
    val colors = MuesliTheme.colors
    OnboardingCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OnboardingStatusTile(done = false, icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}
