package com.phequals7.muesli.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.meetings.MeetingRecordingController
import com.phequals7.muesli.theme.ClinitexPalette
import com.phequals7.muesli.theme.ClinitexType
import com.phequals7.muesli.ui.components.ScreenBackground
import com.phequals7.muesli.ui.dashboard.meetings.MeetingDetailView
import com.phequals7.muesli.ui.dashboard.meetings.MeetingsTab
import com.phequals7.muesli.ui.dashboard.settings.SettingsTab
import com.phequals7.muesli.ui.dashboard.voicenotes.VoiceNoteDetailView
import com.phequals7.muesli.ui.dashboard.voicenotes.VoiceNotesTab
import com.phequals7.muesli.ui.recording.RecordingScreen

enum class DashboardTab(val label: String, val icon: ImageVector) {
    MEETINGS("Réunions", Icons.Outlined.Groups),
    VOICE_NOTES("Vocaux", Icons.Filled.GraphicEq),
    SETTINGS("Réglages", Icons.Outlined.Settings),
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { SharedStore(context) }

    var activeTab by remember { mutableStateOf(DashboardTab.MEETINGS) }
    // Detail screens are full-screen (no bottom navigation, own bottom action bar).
    var openSessionId by remember { mutableStateOf<String?>(null) }

    // A running capture (meeting or voice note) takes the whole screen.
    if (MeetingRecordingController.isActive) {
        Box(modifier = modifier.fillMaxSize()) { RecordingScreen() }
        return
    }

    val detailId = openSessionId
    if (detailId != null) {
        var session by remember(detailId) { mutableStateOf<RecordingSession?>(null) }
        LaunchedEffect(detailId) {
            // Live row: summary state changes show up immediately. Deleted → back to the list.
            store.getRecordingSessionFlow(detailId).collect { s ->
                if (s == null) openSessionId = null else session = s
            }
        }
        BackHandler { openSessionId = null }
        val current = session
        Box(modifier = modifier.fillMaxSize()) {
            if (current != null) {
                if (current.kind == RecordingSession.KIND_VOICE_NOTE) {
                    VoiceNoteDetailView(store = store, note = current, onBack = { openSessionId = null })
                } else {
                    MeetingDetailView(
                        store = store,
                        meeting = current,
                        onBack = { openSessionId = null },
                        onDeleted = { openSessionId = null },
                    )
                }
            } else {
                ScreenBackground(ClinitexPalette.Paper)
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            ClinitexBottomNav(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                DashboardTab.MEETINGS -> MeetingsTab(
                    store = store,
                    onOpenMeeting = { openSessionId = it },
                )
                DashboardTab.VOICE_NOTES -> VoiceNotesTab(
                    store = store,
                    onOpenNote = { openSessionId = it },
                )
                DashboardTab.SETTINGS -> SettingsTab(store)
            }
        }
    }
}

/**
 * Floating pill navigation (Clinitex): white, 1dp line border, light blue
 * shadow; the active tab is a solid blue pill with white label.
 */
@Composable
private fun ClinitexBottomNav(
    activeTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = ClinitexPalette.Blue.copy(alpha = 0.10f),
                spotColor = ClinitexPalette.Blue.copy(alpha = 0.14f),
            )
            .clip(CircleShape)
            .background(ClinitexPalette.Paper)
            .border(1.dp, ClinitexPalette.Line, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DashboardTab.entries.forEach { tab ->
            val selected = tab == activeTab
            val content = if (selected) ClinitexPalette.Paper else ClinitexPalette.InkSoft
            Row(
                modifier = Modifier
                    .weight(if (selected) 1.2f else 1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) ClinitexPalette.Blue else Color.Transparent)
                    .clickable { onTabSelected(tab) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tab.label,
                    style = ClinitexType.body(13.sp, if (selected) FontWeight.Bold else FontWeight.Medium, 16.sp),
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}
