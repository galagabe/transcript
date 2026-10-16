package com.phequals7.muesli.ui.dashboard.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.audio.AudioInputRouteManager
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.CustomWord
import com.phequals7.muesli.meetings.MeetingTemplate
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModel
import com.phequals7.muesli.model.SpeechModels
import com.phequals7.muesli.engine.SherpaRecognizerHolder
import com.phequals7.muesli.theme.AppearanceController
import com.phequals7.muesli.theme.MuesliAccentTheme
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.components.MuesliSurfaceCard
import kotlinx.coroutines.launch

/**
 * Settings hub — port of the muesli-ios SettingsView structure:
 * a grouped list of navigation rows leading to section screens.
 */
@Composable
fun SettingsTab(store: SharedStore) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    val current = section
    if (current == null) {
        SettingsHub(onOpenSection = { section = it })
    } else {
        SettingsScaffold(current.title, onBack = { section = null }) {
            when (current) {
                SettingsSection.MEETINGS -> MeetingsSettings(store)
                SettingsSection.DICTIONARY -> DictionarySettings(store)
                SettingsSection.MODELS -> ModelsSettings()
                SettingsSection.APPEARANCE -> AppearanceSettings()
                SettingsSection.ABOUT -> AboutSettings(store)
            }
        }
    }
}

private enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    MEETINGS("Réunions", "Modèle de compte rendu, micro et intervenants.", Icons.Default.Groups),
    DICTIONARY("Dictionnaire", "Mots d’hésitation, termes métier, noms et sigles.", Icons.Default.Book),
    MODELS("Modèle de transcription", "Téléchargement du modèle Parakeet sur le téléphone.", Icons.Default.Memory),
    APPEARANCE("Apparence", "Mode clair ou sombre, couleur d’accent.", Icons.Default.Palette),
    ABOUT("À propos", "Version, profil et mentions open source.", Icons.Default.Info),
}

// ───────────────────────────── hub ─────────────────────────────

@Composable
private fun SettingsHub(onOpenSection: (SettingsSection) -> Unit) {
    val colors = MuesliTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MuesliSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
    ) {
        item {
            Column {
                Text("Réglages", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text("Réunions, modèle local et confidentialité.", color = colors.textSecondary, fontSize = 13.sp)
            }
        }

        item {
            MuesliSurfaceCard {
                Column {
                    SettingsSection.entries.forEachIndexed { index, section ->
                        SettingsHubRow(
                            section = section,
                            onClick = { onOpenSection(section) }
                        )
                        if (index < SettingsSection.entries.lastIndex) {
                            HorizontalDivider(
                                color = colors.surfaceBorder,
                                modifier = Modifier.padding(start = 64.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHubRow(
    section: SettingsSection,
    onClick: () -> Unit,
) {
    val colors = MuesliTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(MuesliSpacing.s16)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(MuesliCorners.small))
                .background(colors.brandBlueSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(section.icon, contentDescription = null, tint = colors.accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(section.title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(section.subtitle, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MuesliTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MuesliSpacing.s8, vertical = MuesliSpacing.s8)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = colors.textPrimary)
            }
            Text(title, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = MuesliSpacing.s16, vertical = MuesliSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
        ) {
            item { content() }
        }
    }
}

// ─────────────────────── shared row components ───────────────────────

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MuesliTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 14.sp)
            Text(subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    accentWhenSelected: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MuesliTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MuesliCorners.small))
            .clickable { onClick() }
            .padding(vertical = MuesliSpacing.s8)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected && accentWhenSelected) colors.accent else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    MuesliSurfaceCard {
        Column(
            modifier = Modifier.padding(MuesliSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
            content = { content() }
        )
    }
}

// ─────────────────────── Meetings ───────────────────────

@Composable
private fun MeetingsSettings(store: SharedStore) {
    val colors = MuesliTheme.colors
    val context = LocalContext.current
    var defaultTemplate by remember { mutableStateOf(MeetingTemplate.fromId(store.defaultMeetingTemplate)) }
    var micPreference by remember {
        mutableStateOf(AudioInputRouteManager.MicPreference.fromId(store.micPreference))
    }
    val routeManager = remember { AudioInputRouteManager(context) }
    var retainAudio by remember { mutableStateOf(store.retainMeetingAudio) }
    var speakerLabels by remember { mutableStateOf(store.speakerLabelsEnabled) }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Modèle de compte rendu par défaut", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            MeetingTemplate.entries.forEach { template ->
                ChoiceRow(
                    title = template.label,
                    subtitle = template.detail,
                    selected = template == defaultTemplate,
                    onClick = {
                        defaultTemplate = template
                        store.defaultMeetingTemplate = template.id
                    }
                )
            }
        }

        SettingsCard {
            Text("Micro d’enregistrement", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            AudioInputRouteManager.MicPreference.entries.forEach { option ->
                ChoiceRow(
                    title = option.label,
                    subtitle = micPreferenceSubtitle(routeManager, option),
                    selected = micPreference == option,
                    onClick = { micPreference = option; store.micPreference = option.id }
                )
            }
        }

        SettingsCard {
            Text("Enregistrement", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ToggleRow(
                title = "Identifier les intervenants",
                subtitle = "À l’arrêt, sépare les voix sur le téléphone et regroupe la transcription par intervenant. Ajoute une courte étape de traitement.",
                checked = speakerLabels,
                onCheckedChange = { speakerLabels = it; store.speakerLabelsEnabled = it }
            )
            ToggleRow(
                title = "Conserver l’audio des réunions",
                subtitle = "Garde le fichier WAV sur le téléphone pour la réécoute et la séparation des intervenants.",
                checked = retainAudio,
                onCheckedChange = { retainAudio = it; store.retainMeetingAudio = it }
            )
        }
    }
}

// ─────────────────────── Dictionary ───────────────────────

@Composable
private fun DictionarySettings(store: SharedStore) {
    val colors = MuesliTheme.colors
    val scope = rememberCoroutineScope()

    var fillerEnabled by remember { mutableStateOf(store.isFillerWordRemovalEnabled) }
    var customDictEnabled by remember { mutableStateOf(store.isCustomDictionaryEnabled) }
    var customWords by remember { mutableStateOf(emptyList<CustomWord>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var entryWord by remember { mutableStateOf("") }
    var entryReplacement by remember { mutableStateOf("") }
    var entryThreshold by remember { mutableStateOf(0.85f) }

    LaunchedEffect(Unit) {
        store.getCustomWordsFlow().collect { customWords = it }
    }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Traitement du texte", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ToggleRow(
                title = "Supprimer les mots d’hésitation",
                subtitle = "Retire les hésitations (« euh », « heu », « hum »…).",
                checked = fillerEnabled,
                onCheckedChange = { fillerEnabled = it; store.isFillerWordRemovalEnabled = it }
            )
            ToggleRow(
                title = "Dictionnaire personnalisé",
                subtitle = "Corrige automatiquement les termes reconnus de façon approximative.",
                checked = customDictEnabled,
                onCheckedChange = { customDictEnabled = it; store.isCustomDictionaryEnabled = it }
            )
        }

        SettingsCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mots personnalisés", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(MuesliCorners.medium),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajouter", fontSize = 12.sp)
                }
            }

            if (customWords.isEmpty()) {
                Text(
                    "Aucun mot personnalisé pour l’instant. Ajoutez vos termes métier : ils seront corrigés même s’ils sont mal transcrits (similarité Jaro-Winkler).",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                customWords.forEach { word ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(word.word, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (word.replacement != null) {
                                Text("→ ${word.replacement}", color = colors.accent, fontSize = 12.sp)
                            }
                            Text(
                                "Seuil : ${"%.2f".format(word.matchingThreshold)}",
                                color = colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(onClick = { scope.launch { store.deleteCustomWordById(word.id) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = colors.destructive.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = colors.backgroundRaised,
            title = { Text("Ajouter un mot personnalisé", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(value = entryWord, onValueChange = { entryWord = it }, label = { Text("Mot ou expression") })
                    TextField(value = entryReplacement, onValueChange = { entryReplacement = it }, label = { Text("Remplacement (facultatif)") })
                    Column {
                        Text("Seuil de correspondance : ${"%.2f".format(entryThreshold)}", color = colors.textSecondary, fontSize = 12.sp)
                        Slider(value = entryThreshold, onValueChange = { entryThreshold = it }, valueRange = 0.5f..1.0f)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (entryWord.isNotBlank()) {
                            scope.launch {
                                store.insertCustomWord(
                                    CustomWord(
                                        word = entryWord,
                                        replacement = entryReplacement.ifBlank { null },
                                        matchingThreshold = entryThreshold.toDouble()
                                    )
                                )
                            }
                            showAddDialog = false
                            entryWord = ""
                            entryReplacement = ""
                            entryThreshold = 0.85f
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Annuler", color = colors.textSecondary) }
            }
        )
    }
}

private fun micPreferenceSubtitle(
    routeManager: AudioInputRouteManager,
    option: AudioInputRouteManager.MicPreference,
): String = when (option) {
    AudioInputRouteManager.MicPreference.AUTO -> "Suit l’entrée audio par défaut du système"
    AudioInputRouteManager.MicPreference.PHONE -> "Utilise toujours le micro intégré du téléphone"
    AudioInputRouteManager.MicPreference.BLUETOOTH ->
        if (routeManager.isAvailable(option)) "Connecté : utilise le micro Bluetooth (casque, écouteurs)" else "Non connecté : repli sur le micro du téléphone"
    AudioInputRouteManager.MicPreference.USB ->
        if (routeManager.isAvailable(option)) "Connecté : utilise le micro USB externe" else "Non connecté : repli sur le micro du téléphone"
}

// ─────────────────────── Models ───────────────────────

@Composable
private fun ModelsSettings() {
    val colors = MuesliTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        // Single-model catalog (Parakeet v3): information + download/delete, no picker.
        SpeechModelCard(model = SpeechModels.PARAKEET_V3)
        SettingsCard {
            Text("Transcription sur le téléphone", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "La transcription s’exécute entièrement sur le téléphone grâce à sherpa-onnx : l’audio ne quitte jamais l’appareil. Seule la transcription texte est envoyée pour générer le compte rendu.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

// ─────────────────────── Appearance ───────────────────────

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current
    val colors = MuesliTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Thème", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            listOf("system" to "Système", "light" to "Clair", "dark" to "Sombre").forEach { (mode, label) ->
                ChoiceRow(
                    title = label,
                    subtitle = when (mode) {
                        "system" -> "Suit le réglage de mode sombre du téléphone"
                        "light" -> "Toujours utiliser l’apparence claire"
                        else -> "Toujours utiliser l’apparence sombre"
                    },
                    selected = AppearanceController.appearanceMode == mode,
                    onClick = { AppearanceController.setAppearanceMode(context, mode) }
                )
            }
        }

        SettingsCard {
            Text("Couleur d’accent", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            MuesliAccentTheme.entries.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MuesliCorners.small))
                        .clickable { AppearanceController.setAccentTheme(context, theme.id) }
                        .padding(vertical = MuesliSpacing.s8)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(theme.color(dark = true))
                    )
                    Text(
                        theme.label,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (AppearanceController.accentThemeId == theme.id) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (AppearanceController.accentThemeId == theme.id) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────── About ───────────────────────

@Composable
private fun AboutSettings(store: SharedStore) {
    val colors = MuesliTheme.colors
    val context = LocalContext.current
    var profileName by remember { mutableStateOf(store.userProfileName) }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Profil", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = profileName,
                onValueChange = {
                    profileName = it
                    store.userProfileName = it
                },
                label = { Text("Votre nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard {
            Text("Clindy", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Version", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                Text("${info.versionName} (${info.longVersionCode})", color = colors.textPrimary, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Moteur de reconnaissance", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("sherpa-onnx 1.13.4", color = colors.textPrimary, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Modèle", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(SpeechModels.selected(context).displayName, color = colors.textPrimary, fontSize = 13.sp)
            }
        }

        SettingsCard {
            Text("Mentions open source", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Basé sur Muesli pour Android (licence MIT, © auteurs de Muesli). sherpa-onnx (k2-fsa, Apache-2.0) · ONNX Runtime (Microsoft, MIT) · modèles Parakeet (NVIDIA NeMo) · Silero VAD.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

// ─────────────────────── shared cards ───────────────────────

/**
 * Model management card for the sherpa-onnx Parakeet V3 runtime: download
 * (resumable, with progress), status display, and deletion.
 */
@Composable
internal fun SpeechModelCard(
    model: SpeechModel,
) {
    val colors = MuesliTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember(model.id) { ModelManager(context.applicationContext, model) }

    var isDownloaded by remember(model.id) { mutableStateOf(modelManager.isDownloaded()) }
    var isDownloading by remember(model.id) { mutableStateOf(false) }
    var progress by remember(model.id) { mutableStateOf<ModelManager.DownloadProgress?>(null) }
    var statusMessage by remember(model.id) { mutableStateOf<String?>(null) }
    var downloadJob by remember(model.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    MuesliSurfaceCard {
        Column(
            modifier = Modifier.padding(MuesliSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(MuesliCorners.small))
                        .background(
                            if (isDownloaded) colors.syncGreenSubtle else colors.brandBlueSubtle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isDownloaded) colors.syncGreen else colors.accent
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(model.capabilityLabel, color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = when {
                            isDownloading && progress?.extracting == true -> "Extraction de ${progress!!.currentFile}…"
                            isDownloading && progress != null ->
                                "${(progress!!.fraction * 100).toInt()} % · ${modelManager.formatBytes(progress!!.totalBytesDone)} sur ${modelManager.formatBytes(progress!!.totalBytes)}"
                            isDownloaded -> "Téléchargé · ${modelManager.formatBytes(modelManager.downloadedBytes())} sur le téléphone"
                            modelManager.downloadedBytes() > 0 ->
                                "Téléchargement partiel · ${modelManager.formatBytes(modelManager.downloadedBytes())} / ${modelManager.formatBytes(model.totalSizeBytes)}"
                            else -> "Non téléchargé · ${modelManager.formatBytes(model.totalSizeBytes)} nécessaires"
                        },
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Text(model.detail, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)

            if (isDownloading && progress != null) {
                LinearProgressIndicator(
                    progress = { progress!!.fraction },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(MuesliCorners.small)),
                    color = colors.accent,
                    trackColor = colors.surfacePrimary,
                )
            }

            statusMessage?.let {
                Text(it, color = colors.destructive, fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8)) {
                if (isDownloading) {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        downloadJob = null
                        isDownloading = false
                        progress = null
                    }) {
                        Text("Annuler", color = colors.textSecondary)
                    }
                } else if (!isDownloaded) {
                    Button(
                        onClick = {
                            statusMessage = null
                            isDownloading = true
                            downloadJob = scope.launch {
                                try {
                                    modelManager.download { p -> progress = p }
                                    isDownloaded = true
                                    statusMessage = null
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    statusMessage = "Échec du téléchargement : ${t.message}"
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium)
                    ) {
                        Text(
                            if (modelManager.downloadedBytes() > 0) "Reprendre le téléchargement" else "Télécharger",
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text("Actif", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = MuesliSpacing.s8, vertical = MuesliSpacing.s8))
                    TextButton(onClick = {
                        modelManager.deleteModel()
                        SherpaRecognizerHolder.reset()
                        isDownloaded = false
                    }) {
                        Text("Supprimer", color = colors.destructive)
                    }
                }
            }
        }
    }
}
