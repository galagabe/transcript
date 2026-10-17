package com.phequals7.muesli.ui.dashboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
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
import com.phequals7.muesli.audio.AudioInputRouteManager
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.CustomWord
import com.phequals7.muesli.engine.SherpaRecognizerHolder
import com.phequals7.muesli.meetings.MeetingTemplate
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModel
import com.phequals7.muesli.model.SpeechModels
import com.phequals7.muesli.theme.ClinitexPalette
import com.phequals7.muesli.theme.ClinitexType
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.ui.components.BrandCard
import com.phequals7.muesli.ui.components.BrandInput
import com.phequals7.muesli.ui.components.DisplayTitle
import com.phequals7.muesli.ui.components.Eyebrow
import com.phequals7.muesli.ui.components.FieldLabel
import com.phequals7.muesli.ui.components.IconTile
import com.phequals7.muesli.ui.components.NoticeBox
import com.phequals7.muesli.ui.components.NoticeTone
import com.phequals7.muesli.ui.components.PillButton
import com.phequals7.muesli.ui.components.PillVariant
import com.phequals7.muesli.ui.components.RoundIconButton
import com.phequals7.muesli.ui.components.ScreenBackground
import com.phequals7.muesli.ui.components.ScreenHeader
import com.phequals7.muesli.ui.components.StatusBadge
import com.phequals7.muesli.ui.components.TileStyle
import com.phequals7.muesli.ui.components.brandSwitchColors
import com.phequals7.muesli.ui.dashboard.BadgeTone
import kotlinx.coroutines.launch

/**
 * Settings hub: a grouped list of rows leading to section screens. Light
 * only: the Muesli appearance (dark mode, accent color) section is gone.
 */
@Composable
fun SettingsTab(store: SharedStore) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    ScreenBackground(ClinitexPalette.PaperTint)

    val current = section
    if (current == null) {
        SettingsHub(onOpenSection = { section = it })
    } else {
        BackHandler { section = null }
        SettingsScaffold(current.title, onBack = { section = null }) {
            when (current) {
                SettingsSection.MEETINGS -> MeetingsSettings(store)
                SettingsSection.DICTIONARY -> DictionarySettings(store)
                SettingsSection.MODELS -> ModelsSettings()
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
    MEETINGS("Réunions", "Modèle de compte rendu, micro et intervenants.", Icons.Outlined.Groups),
    DICTIONARY("Dictionnaire", "Mots d’hésitation, termes métier, noms et sigles.", Icons.Outlined.Book),
    MODELS("Modèle de transcription", "Téléchargement du modèle Parakeet sur le téléphone.", Icons.Outlined.Memory),
    ABOUT("À propos", "Version, profil et mentions open source.", Icons.Outlined.Info),
}

// ───────────────────────────── hub ─────────────────────────────

@Composable
private fun SettingsHub(onOpenSection: (SettingsSection) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.PaperTint),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader("Réglages", intro = "Réunions, modèle local et confidentialité.")
        }

        item {
            BrandCard(padding = PaddingValues(vertical = 4.dp), spacing = 0.dp) {
                SettingsSection.entries.forEachIndexed { index, section ->
                    SettingsHubRow(section = section, onClick = { onOpenSection(section) })
                    if (index < SettingsSection.entries.lastIndex) {
                        HorizontalDivider(color = ClinitexPalette.Line, modifier = Modifier.padding(start = 72.dp))
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        IconTile(section.icon, TileStyle.SOFT, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(section.title, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
            Text(section.subtitle, style = ClinitexType.body(12.sp, FontWeight.Normal, 17.sp), color = ClinitexPalette.Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ClinitexPalette.Blue)
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.PaperTint)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 14.dp)
        ) {
            RoundIconButton(Icons.Default.ChevronLeft, "Retour aux réglages", onBack, iconSize = 28.dp)
            DisplayTitle(title, size = 22.sp, lineHeight = 24.sp, maxLines = 2, modifier = Modifier.padding(start = 4.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        ) {
            item { content() }
        }
    }
}

// ─────────────────────── shared row components ───────────────────────

@Composable
private fun CardTitle(text: String) = Eyebrow(text)

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
            Text(subtitle, style = ClinitexType.body(12.sp, FontWeight.Normal, 17.sp), color = ClinitexPalette.Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = brandSwitchColors())
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(MuesliCorners.input)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) ClinitexPalette.PaperTint else ClinitexPalette.Paper)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = ClinitexType.body(15.sp, if (selected) FontWeight.Bold else FontWeight.Medium, 20.sp),
                color = if (selected) ClinitexPalette.Blue else ClinitexPalette.Ink,
            )
            Text(subtitle, style = ClinitexType.body(12.sp, FontWeight.Normal, 17.sp), color = ClinitexPalette.Muted)
        }
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ClinitexPalette.Blue)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = ClinitexPalette.Paper, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    BrandCard(padding = PaddingValues(16.dp), spacing = 12.dp, content = content)
}

// ─────────────────────── Meetings ───────────────────────

@Composable
private fun MeetingsSettings(store: SharedStore) {
    val context = LocalContext.current
    var defaultTemplate by remember { mutableStateOf(MeetingTemplate.meetingFromId(store.defaultMeetingTemplate)) }
    var micPreference by remember {
        mutableStateOf(AudioInputRouteManager.MicPreference.fromId(store.micPreference))
    }
    val routeManager = remember { AudioInputRouteManager(context) }
    var retainAudio by remember { mutableStateOf(store.retainMeetingAudio) }
    var speakerLabels by remember { mutableStateOf(store.speakerLabelsEnabled) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            CardTitle("Modèle de compte rendu par défaut")
            // Voice-note template excluded: it is used by the Vocaux tab only.
            MeetingTemplate.meetingTemplates.forEach { template ->
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
            CardTitle("Micro d’enregistrement")
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
            CardTitle("Enregistrement")
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            CardTitle("Traitement du texte")
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) { CardTitle("Mots personnalisés") }
                PillButton(
                    "Ajouter",
                    onClick = { showAddDialog = true },
                    icon = Icons.Default.Add,
                    height = 36.dp,
                    fontSize = 13.sp,
                )
            }

            if (customWords.isEmpty()) {
                Text(
                    "Aucun mot personnalisé pour l’instant. Ajoutez vos termes métier : ils seront corrigés même s’ils sont mal transcrits (similarité Jaro-Winkler).",
                    style = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp),
                    color = ClinitexPalette.Muted,
                )
            } else {
                customWords.forEachIndexed { index, word ->
                    if (index > 0) HorizontalDivider(color = ClinitexPalette.Line)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(word.word, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
                            if (word.replacement != null) {
                                Text("→ ${word.replacement}", style = ClinitexType.body(13.sp, FontWeight.Medium, 18.sp), color = ClinitexPalette.Blue)
                            }
                            Text(
                                "Seuil : ${"%.2f".format(word.matchingThreshold)}",
                                style = ClinitexType.body(11.sp, FontWeight.Normal, 15.sp),
                                color = ClinitexPalette.Muted,
                            )
                        }
                        RoundIconButton(
                            Icons.Outlined.Delete,
                            "Supprimer ${word.word}",
                            { scope.launch { store.deleteCustomWordById(word.id) } },
                            tint = ClinitexPalette.Muted,
                            iconSize = 20.dp,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = ClinitexPalette.Paper,
            shape = RoundedCornerShape(MuesliCorners.card),
            title = { Text("Ajouter un mot personnalisé", style = ClinitexType.body(18.sp, FontWeight.Bold, 24.sp), color = ClinitexPalette.Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Mot ou expression")
                        BrandInput(value = entryWord, onValueChange = { entryWord = it }, placeholder = "Ex. : autolaveuse")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Remplacement (facultatif)")
                        BrandInput(value = entryReplacement, onValueChange = { entryReplacement = it })
                    }
                    Column {
                        FieldLabel("Seuil de correspondance : ${"%.2f".format(entryThreshold)}")
                        Slider(
                            value = entryThreshold,
                            onValueChange = { entryThreshold = it },
                            valueRange = 0.5f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ClinitexPalette.Blue,
                                activeTrackColor = ClinitexPalette.Blue,
                                inactiveTrackColor = ClinitexPalette.Line,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                PillButton(
                    "Ajouter",
                    onClick = {
                        if (entryWord.isNotBlank()) {
                            scope.launch {
                                store.insertCustomWord(
                                    CustomWord(
                                        word = entryWord.trim(),
                                        replacement = entryReplacement.trim().ifBlank { null },
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
                    enabled = entryWord.isNotBlank(),
                    height = 44.dp,
                    fontSize = 14.sp,
                )
            },
            dismissButton = {
                PillButton("Annuler", onClick = { showAddDialog = false }, variant = PillVariant.OUTLINE, height = 44.dp, fontSize = 14.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Single-model catalog (Parakeet v3): information + download/delete, no picker.
        SpeechModelCard(model = SpeechModels.PARAKEET_V3)
        SettingsCard {
            CardTitle("Transcription sur le téléphone")
            Text(
                "La transcription s’exécute entièrement sur le téléphone grâce à sherpa-onnx : l’audio ne quitte jamais l’appareil. Seule la transcription texte est envoyée pour générer le compte rendu.",
                style = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp),
                color = ClinitexPalette.InkSoft,
            )
        }
    }
}

// ─────────────────────── About ───────────────────────

@Composable
private fun AboutSettings(store: SharedStore) {
    val context = LocalContext.current
    var profileName by remember { mutableStateOf(store.userProfileName) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            CardTitle("Profil")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Votre nom")
                BrandInput(
                    value = profileName,
                    onValueChange = {
                        profileName = it
                        store.userProfileName = it
                    },
                    placeholder = "Ex. : Camille",
                )
            }
        }

        SettingsCard {
            CardTitle("Clindy")
            val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
            InfoRow("Version", "${info.versionName} (${info.longVersionCode})")
            InfoRow("Moteur de reconnaissance", "sherpa-onnx 1.13.4")
            InfoRow("Modèle", SpeechModels.selected(context).displayName)
        }

        SettingsCard {
            CardTitle("Mentions open source")
            Text(
                "Basé sur Muesli pour Android (licence MIT, © auteurs de Muesli). sherpa-onnx (k2-fsa, Apache-2.0) · ONNX Runtime (Microsoft, MIT) · modèles Parakeet (NVIDIA NeMo) · Silero VAD.",
                style = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp),
                color = ClinitexPalette.InkSoft,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = ClinitexType.body(14.sp), color = ClinitexPalette.Muted, modifier = Modifier.weight(1f))
        Text(value, style = ClinitexType.body(14.sp, FontWeight.Medium, 20.sp), color = ClinitexPalette.Ink)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember(model.id) { ModelManager(context.applicationContext, model) }

    var isDownloaded by remember(model.id) { mutableStateOf(modelManager.isDownloaded()) }
    var isDownloading by remember(model.id) { mutableStateOf(false) }
    var progress by remember(model.id) { mutableStateOf<ModelManager.DownloadProgress?>(null) }
    var statusMessage by remember(model.id) { mutableStateOf<String?>(null) }
    var downloadJob by remember(model.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IconTile(
                if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                if (isDownloaded) TileStyle.FILLED else TileStyle.SOFT,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.displayName, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
                Text(model.capabilityLabel, style = ClinitexType.body(12.sp, FontWeight.Medium, 16.sp), color = ClinitexPalette.Blue)
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
                    style = ClinitexType.body(12.sp, FontWeight.Normal, 16.sp),
                    color = ClinitexPalette.Muted,
                )
            }
        }

        Text(model.detail, style = ClinitexType.body(13.sp, FontWeight.Normal, 19.sp), color = ClinitexPalette.InkSoft)

        if (isDownloading && progress != null) {
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

        statusMessage?.let { NoticeBox(it, NoticeTone.DANGER) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isDownloading) {
                PillButton(
                    "Annuler",
                    onClick = {
                        downloadJob?.cancel()
                        downloadJob = null
                        isDownloading = false
                        progress = null
                    },
                    variant = PillVariant.OUTLINE,
                    height = 40.dp,
                    fontSize = 14.sp,
                )
            } else if (!isDownloaded) {
                PillButton(
                    if (modelManager.downloadedBytes() > 0) "Reprendre le téléchargement" else "Télécharger",
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
                    icon = Icons.Default.CloudDownload,
                    height = 40.dp,
                    fontSize = 14.sp,
                )
            } else {
                StatusBadge("Actif", BadgeTone.READY, withCheck = true)
                Spacer(Modifier.weight(1f))
                PillButton(
                    "Supprimer",
                    onClick = {
                        modelManager.deleteModel()
                        SherpaRecognizerHolder.reset()
                        isDownloaded = false
                    },
                    // Outline (blue), not peach: never two complementaries side by side (mint badge).
                    variant = PillVariant.OUTLINE,
                    height = 40.dp,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
