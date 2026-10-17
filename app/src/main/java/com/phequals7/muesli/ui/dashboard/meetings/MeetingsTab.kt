package com.phequals7.muesli.ui.dashboard.meetings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.meetings.MeetingRecordingController
import com.phequals7.muesli.meetings.MeetingTemplate
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModels
import com.phequals7.muesli.theme.ClinitexPalette
import com.phequals7.muesli.theme.ClinitexType
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.ui.components.BrandCard
import com.phequals7.muesli.ui.components.BrandInput
import com.phequals7.muesli.ui.components.EmptyState
import com.phequals7.muesli.ui.components.Eyebrow
import com.phequals7.muesli.ui.components.FieldLabel
import com.phequals7.muesli.ui.components.IconTile
import com.phequals7.muesli.ui.components.NoticeBox
import com.phequals7.muesli.ui.components.NoticeTone
import com.phequals7.muesli.ui.components.PillButton
import com.phequals7.muesli.ui.components.ScreenBackground
import com.phequals7.muesli.ui.components.ScreenHeader
import com.phequals7.muesli.ui.components.StatusBadge
import com.phequals7.muesli.ui.components.TileStyle
import com.phequals7.muesli.ui.components.brandUpper
import com.phequals7.muesli.ui.dashboard.DisplayFormat
import com.phequals7.muesli.ui.dashboard.SessionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Meetings home (Reunions.dc.html): new-meeting card (title + template +
 * pill "Démarrer l’enregistrement") and searchable recent meetings with
 * status badges. Only RecordingSession.kind == "meeting" rows are listed.
 */
@Composable
fun MeetingsTab(
    store: SharedStore,
    onOpenMeeting: (String) -> Unit,
) {
    val context = LocalContext.current

    var meetings by remember { mutableStateOf(emptyList<RecordingSession>()) }
    var titleInput by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(MeetingTemplate.meetingFromId(store.defaultMeetingTemplate)) }
    var searchQuery by remember { mutableStateOf("") }

    val modelReady = remember { ModelManager(context.applicationContext).isDownloaded() }

    ScreenBackground(ClinitexPalette.PaperTint)

    LaunchedEffect(Unit) {
        store.getRecordingSessionsFlow(RecordingSession.KIND_MEETING).collect { meetings = it }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startMeeting() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val title = titleInput.trim().ifBlank {
            "Réunion du ${SimpleDateFormat("d MMM 'à' HH:mm", Locale.FRENCH).format(Date())}"
        }
        MeetingRecordingController.start(
            context, UUID.randomUUID().toString(), title, selectedTemplate.id, RecordingSession.KIND_MEETING
        )
        titleInput = ""
    }

    val filtered = meetings.filter {
        searchQuery.isBlank() ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.manualNotes.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ClinitexPalette.PaperTint),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
    ) {
        item { ScreenHeader("Réunions") }

        // ── Start a new meeting ─────────────────────────────────────────
        item {
            BrandCard(modifier = Modifier.padding(top = 18.dp)) {
                Eyebrow("Nouvelle réunion")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Titre")
                    BrandInput(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = "Ex. : RDV Résidence Les Tilleuls",
                    )
                }
                TemplatePicker(
                    selected = selectedTemplate,
                    onSelected = { selectedTemplate = it }
                )
                if (!modelReady) {
                    NoticeBox(
                        "Le modèle ${SpeechModels.selected(context).shortName} est nécessaire : téléchargez-le d’abord dans Réglages.",
                        NoticeTone.WARNING,
                    )
                }
                PillButton(
                    text = "Démarrer l’enregistrement",
                    onClick = { startMeeting() },
                    enabled = modelReady,
                    icon = Icons.Default.Mic,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── Recent meetings ─────────────────────────────────────────────
        item {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
            ) {
                Text(
                    "Réunions récentes".brandUpper(),
                    style = ClinitexType.display(16.sp, 18.sp),
                    color = ClinitexPalette.Blue,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DisplayFormat.savedCount(meetings.size, feminine = true),
                    style = ClinitexType.body(13.sp, FontWeight.Normal, 18.sp),
                    color = ClinitexPalette.Muted,
                )
            }
        }

        item {
            BrandInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Rechercher une réunion",
                leadingIcon = Icons.Default.Search,
                shape = CircleShape,
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Groups,
                    title = if (meetings.isEmpty()) "Aucune réunion enregistrée" else "Aucun résultat",
                    detail = if (meetings.isEmpty()) "Démarrez une réunion ci-dessus pour l’enregistrer." else null,
                )
            }
        } else {
            items(filtered, key = { it.id }) { meeting ->
                SessionRow(
                    session = meeting,
                    icon = Icons.Outlined.Description,
                    meta = listOfNotNull(
                        DisplayFormat.dayMonthTime(meeting.startedAt ?: meeting.createdAt),
                        DisplayFormat.minutes(meeting.startedAt, meeting.endedAt),
                        MeetingTemplate.fromId(meeting.templateId).label,
                    ).joinToString(" · "),
                    onClick = { onOpenMeeting(meeting.id) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }
}

// ─────────────────────────── components ───────────────────────────

@Composable
private fun TemplatePicker(
    selected: MeetingTemplate,
    onSelected: (MeetingTemplate) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(MuesliCorners.input)

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(ClinitexPalette.PaperTint)
                .border(1.dp, ClinitexPalette.Line, shape)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(selected.label, style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp), color = ClinitexPalette.Ink)
                Text(selected.detail, style = ClinitexType.body(12.sp, FontWeight.Normal, 17.sp), color = ClinitexPalette.Muted)
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "Choisir le modèle de compte rendu",
                tint = ClinitexPalette.Blue,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = ClinitexPalette.Paper,
            shape = RoundedCornerShape(MuesliCorners.input),
        ) {
            // Voice-note template excluded: meetings only.
            MeetingTemplate.meetingTemplates.forEach { template ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(template.label, style = ClinitexType.body(14.sp, FontWeight.Bold, 19.sp), color = ClinitexPalette.Ink)
                            Text(template.detail, style = ClinitexType.body(12.sp, FontWeight.Normal, 16.sp), color = ClinitexPalette.Muted)
                        }
                    },
                    onClick = {
                        onSelected(template)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * List row shared by meetings and voice notes: icon tile (dashed when the
 * summary waits for network), bold title, meta line, status badge.
 */
@Composable
internal fun SessionRow(
    session: RecordingSession,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val waiting = session.phase == "completed" && SessionStatus.isWaitingForNetwork(session.summaryState)
    val shape = RoundedCornerShape(MuesliCorners.row)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClinitexPalette.Paper)
            .border(1.dp, ClinitexPalette.Line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        IconTile(
            icon = if (waiting) Icons.Default.WifiOff else icon,
            style = if (waiting) TileStyle.DASHED else TileStyle.FILLED,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                session.title,
                style = ClinitexType.body(15.sp, FontWeight.Bold, 20.sp),
                color = ClinitexPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                meta,
                style = ClinitexType.body(12.sp, FontWeight.Normal, 16.sp),
                color = ClinitexPalette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusBadge(SessionStatus.badge(session.phase, session.summaryState))
    }
}
