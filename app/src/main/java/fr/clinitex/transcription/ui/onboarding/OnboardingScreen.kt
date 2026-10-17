package fr.clinitex.transcription.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clinitex.transcription.R
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.model.ModelManager
import fr.clinitex.transcription.model.SpeechModels
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.ClinitexType
import fr.clinitex.transcription.ui.components.Eyebrow
import fr.clinitex.transcription.ui.components.NoticeBox
import fr.clinitex.transcription.ui.components.NoticeTone
import fr.clinitex.transcription.ui.components.PillButton
import fr.clinitex.transcription.ui.components.PillVariant
import fr.clinitex.transcription.ui.components.ScreenBackground
import fr.clinitex.transcription.ui.components.StatusBadge
import fr.clinitex.transcription.ui.components.brandUpper
import fr.clinitex.transcription.ui.dashboard.BadgeTone
import kotlinx.coroutines.launch

/**
 * Multi-step onboarding: Welcome (Main.dc.html) → Permissions (microphone,
 * notifications) → Speech model download (Parakeet v3) → Done.
 * The optional first name moved to Réglages › À propos (not on the welcome mockup).
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

    fun finish() {
        store.isOnboardingCompleted = true
        onFinished()
    }

    val onNext: () -> Unit = { if (stepIndex < steps.lastIndex) stepIndex++ else finish() }
    val onBack: (() -> Unit)? = if (stepIndex > 0) ({ stepIndex-- }) else null

    Box(modifier = modifier) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(onNext = onNext)
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
private fun WelcomeStep(onNext: () -> Unit) {
    ScreenBackground(ClinitexPalette.Paper)
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.Paper)
    ) {
        // Mint half disc on the right edge (brand shape, flat color).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = 260.dp.toPx()
            drawArc(
                color = ClinitexPalette.Mint,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(size.width - d / 2f, size.height - 120.dp.toPx() - d),
                size = Size(d, d),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    // Official logo, used as is (light background only).
                    Image(
                        painter = painterResource(id = R.drawable.logo_clinitex),
                        contentDescription = "Clinitex, Engagés au sens propre",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(132.dp)
                            .aspectRatio(2500f / 987f),
                    )
                    Spacer(Modifier.weight(1f))
                    Eyebrow("Appli interne", color = ClinitexPalette.Muted, size = 11.sp)
                }

                Spacer(Modifier.height(56.dp))

                Image(
                    painter = painterResource(id = R.drawable.ic_muesli_logo),
                    contentDescription = "Icône de l’application Clindy",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp)),
                )
                Spacer(Modifier.height(22.dp))
                Eyebrow("Clindy", withBar = true, tracking = 0.14f)
                Spacer(Modifier.height(22.dp))
                Text(
                    "Vos réunions,".brandUpper(),
                    style = ClinitexType.display(44.sp, 42.sp),
                    color = ClinitexPalette.Blue,
                )
                // Outlined second line (CSS -webkit-text-stroke equivalent).
                Text(
                    "Résumées.".brandUpper(),
                    style = ClinitexType.display(44.sp, 42.sp).copy(
                        drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                    ),
                    color = ClinitexPalette.Blue,
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    "Enregistrez vos rendez-vous et réunions. La transcription se fait sur le téléphone, le compte rendu arrive dès que vous avez du réseau.",
                    style = ClinitexType.body(16.sp, FontWeight.Normal, 25.sp),
                    color = ClinitexPalette.InkSoft,
                    modifier = Modifier.widthIn(max = 320.dp),
                )

                Spacer(Modifier.height(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeatureRow(Icons.Outlined.Mic, "Un bouton pour enregistrer, même sans réseau")
                    FeatureRow(Icons.Outlined.Lock, "L’audio reste sur le téléphone")
                    FeatureRow(Icons.Outlined.Description, "Un compte rendu clair, prêt à partager")
                }
                Spacer(Modifier.height(24.dp))
            }

            PillButton(
                "Commencer",
                onClick = onNext,
                height = 54.dp,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Basé sur Muesli pour Android, licence MIT",
                style = ClinitexType.body(12.sp, FontWeight.Normal, 18.sp),
                color = ClinitexPalette.Muted,
                textAlign = TextAlign.Center,
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
            status = if (micGranted) "Autorisé" else "Obligatoire pour enregistrer les réunions et les vocaux",
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
    OnboardingCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OnboardingStatusTile(done = granted, icon = icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
                Text(status, style = ClinitexType.body(12.sp, FontWeight.Normal, 17.sp), color = ClinitexPalette.Muted)
            }
            if (!granted) {
                PillButton("Autoriser", onClick = onGrant, height = 38.dp, fontSize = 13.sp)
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
            Text(model.displayName, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
            Text(model.capabilityLabel, style = ClinitexType.body(12.sp, FontWeight.Medium, 16.sp), color = ClinitexPalette.Blue)
            Text(model.detail, style = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp), color = ClinitexPalette.InkSoft)
        }

        OnboardingCard {
            if (downloaded) {
                StatusBadge("Le modèle est prêt", BadgeTone.READY, withCheck = true)
            } else {
                Text(
                    when {
                        downloading && progress?.extracting == true -> "Extraction…"
                        downloading && progress != null ->
                            "${(progress!!.fraction * 100).toInt()} % · ${manager.formatBytes(progress!!.totalBytesDone)} sur ${manager.formatBytes(progress!!.totalBytes)}"
                        else -> "Téléchargement unique nécessaire"
                    },
                    style = ClinitexType.body(14.sp, FontWeight.Bold, 20.sp),
                    color = ClinitexPalette.Ink,
                )
            }
            if (downloading && progress != null) {
                LinearProgressIndicator(
                    progress = { progress!!.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = ClinitexPalette.Blue,
                    trackColor = ClinitexPalette.Line,
                    drawStopIndicator = {},
                )
            }
            error?.let { NoticeBox(it, NoticeTone.DANGER) }
            if (!downloaded) {
                if (downloading) {
                    PillButton(
                        "Annuler le téléchargement",
                        onClick = {
                            downloadJob?.cancel()
                            downloading = false
                            progress = null
                        },
                        variant = PillVariant.OUTLINE,
                        height = 46.dp,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PillButton(
                        if (manager.downloadedBytes() > 0) "Reprendre le téléchargement" else "Télécharger le modèle",
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
                        height = 46.dp,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
        subtitle = "Vous pouvez enregistrer votre première réunion ou dicter un premier vocal.",
        onBack = onBack,
        primaryLabel = "Accéder à Clindy",
        primaryEnabled = true,
        onPrimary = onNext,
    ) {
        DoneRow(
            icon = Icons.Default.Mic,
            title = "Enregistrez",
            detail = "Informez les participants, puis lancez l’enregistrement depuis l’onglet Réunions.",
        )
        DoneRow(
            icon = Icons.Outlined.CloudOff,
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
    OnboardingCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OnboardingStatusTile(done = false, icon = icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
                Text(detail, style = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp), color = ClinitexPalette.Muted)
            }
        }
    }
}
