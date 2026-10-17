package fr.clinitex.transcription.ui.dashboard

import fr.clinitex.transcription.meetings.MeetingTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SessionStatusTest {

    @Test
    fun badgeFollowsPhaseThenSummaryState() {
        assertEquals(SessionBadge("Prêt", BadgeTone.READY), SessionStatus.badge("completed", "completed"))
        assertEquals(SessionBadge("En attente", BadgeTone.PENDING), SessionStatus.badge("completed", "queued"))
        assertEquals(SessionBadge("En attente", BadgeTone.PENDING), SessionStatus.badge("completed", "generating"))
        assertEquals(BadgeTone.FAILED, SessionStatus.badge("completed", "failed").tone)
        assertEquals(BadgeTone.FAILED, SessionStatus.badge("failed", "notStarted").tone)
        assertEquals(BadgeTone.NEUTRAL, SessionStatus.badge("completed", "unavailable").tone)
        assertEquals(BadgeTone.PENDING, SessionStatus.badge("recording", "notStarted").tone)
        assertTrue(SessionStatus.isWaitingForNetwork("queued"))
        assertFalse(SessionStatus.isWaitingForNetwork("generating"))
    }

    @Test
    fun relativeDayLabels() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16, 18, 0, 0) }.timeInMillis
        val today = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16, 17, 42, 0) }.timeInMillis
        val yesterday = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 15, 16, 20, 0) }.timeInMillis
        val older = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 14, 9, 30, 0) }.timeInMillis

        assertEquals("Aujourd’hui à 17:42", DisplayFormat.relativeDayTime(today, now))
        assertEquals("Hier à 16:20", DisplayFormat.relativeDayTime(yesterday, now))
        assertEquals(DisplayFormat.dayMonthTime(older), DisplayFormat.relativeDayTime(older, now))
        assertTrue(DisplayFormat.relativeDayTime(older, now).startsWith("14 "))
    }

    @Test
    fun durationsAndTimer() {
        assertEquals("1 min 12", DisplayFormat.minutesSeconds(0L, 72_000L))
        assertEquals("0 min 40", DisplayFormat.minutesSeconds(0L, 40_000L))
        assertNull(DisplayFormat.minutesSeconds(null, 40_000L))
        assertEquals("45 min", DisplayFormat.minutes(0L, 45 * 60_000L))
        assertEquals("< 1 min", DisplayFormat.minutes(0L, 30_000L))
        assertEquals("12:47", DisplayFormat.timer(767))
        assertEquals("00:05", DisplayFormat.timer(5))
        assertEquals("1:02:03", DisplayFormat.timer(3723))
        assertEquals("1 enregistrée", DisplayFormat.savedCount(1, feminine = true))
        assertEquals("3 enregistrés", DisplayFormat.savedCount(3, feminine = false))
    }

    @Test
    fun voiceNoteTemplateIsHiddenFromMeetingPickers() {
        assertFalse(MeetingTemplate.NOTE_VOCALE in MeetingTemplate.meetingTemplates)
        assertEquals(
            listOf(MeetingTemplate.GENERAL, MeetingTemplate.RDV_COMMERCIAL, MeetingTemplate.REUNION_INTERNE),
            MeetingTemplate.meetingTemplates,
        )
        assertEquals(MeetingTemplate.NOTE_VOCALE, MeetingTemplate.fromId("noteVocale"))
        assertEquals(MeetingTemplate.GENERAL, MeetingTemplate.meetingFromId("noteVocale"))
        assertEquals(MeetingTemplate.RDV_COMMERCIAL, MeetingTemplate.meetingFromId("rdvCommercial"))
        assertEquals(MeetingTemplate.GENERAL, MeetingTemplate.meetingFromId("legacyMuesli"))
    }

    @Test
    fun voiceNoteTemplateKeepsItsHeadings() {
        val instructions = MeetingTemplate.NOTE_VOCALE.instructions
        assertEquals("Note vocale", MeetingTemplate.NOTE_VOCALE.label)
        assertTrue(instructions.startsWith("Mets au propre ce message dicté, sans rien inventer ni ajouter.\n\n## Note\n"))
        assertTrue(instructions.contains("\n\n## À faire\n- [ ] "))
        assertTrue(instructions.endsWith("Si aucune action n'est citée, écris « Rien de noté »."))
    }
}
