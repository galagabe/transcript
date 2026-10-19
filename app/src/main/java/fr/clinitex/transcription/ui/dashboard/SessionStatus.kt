package fr.clinitex.transcription.ui.dashboard

import fr.clinitex.transcription.data.entity.RecordingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Visual tone of a status badge (Clinitex: success = mint, danger = peach, pending = blue outline). */
enum class BadgeTone { READY, PENDING, FAILED, NEUTRAL }

data class SessionBadge(val label: String, val tone: BadgeTone)

/**
 * Status shown on meeting and voice-note rows, from the recording phase and
 * the deferred summary state. Pure logic (unit-tested).
 */
object SessionStatus {

    fun badge(phase: String, summaryState: String): SessionBadge = when (phase) {
        "recording" -> SessionBadge("En cours", BadgeTone.PENDING)
        "transcribing" -> SessionBadge("Transcription", BadgeTone.PENDING)
        "failed" -> SessionBadge("Échec", BadgeTone.FAILED)
        "cancelled" -> SessionBadge("Annulé", BadgeTone.NEUTRAL)
        "completed" -> when (summaryState) {
            "completed" -> SessionBadge("Prêt", BadgeTone.READY)
            "queued", "generating" -> SessionBadge("En attente", BadgeTone.PENDING)
            "failed" -> SessionBadge("Échec", BadgeTone.FAILED)
            else -> SessionBadge("Transcrit", BadgeTone.NEUTRAL)
        }
        else -> SessionBadge("Transcrit", BadgeTone.NEUTRAL)
    }

    /** The summary waits for the phone to be back online. */
    fun isWaitingForNetwork(summaryState: String): Boolean = summaryState == "queued"

    /**
     * Lot 6b "Forcer la reconnexion" banner text: "2 synthèses en attente · 1 en échec";
     * null (no banner) when no summary waits for the network or has failed.
     */
    fun pendingSummariesLabel(sessions: List<RecordingSession>): String? {
        val done = sessions.filter { it.phase == "completed" }
        val waiting = done.count { it.summaryState == "queued" }
        val failed = done.count { it.summaryState == "failed" }
        fun summaries(n: Int) = if (n > 1) "$n synthèses" else "$n synthèse"
        return when {
            waiting > 0 && failed > 0 -> "${summaries(waiting)} en attente · $failed en échec"
            waiting > 0 -> "${summaries(waiting)} en attente"
            failed > 0 -> "${summaries(failed)} en échec"
            else -> null
        }
    }
}

/** French date / duration labels used by the lists and detail screens. */
object DisplayFormat {

    private fun format(pattern: String, ms: Long) = SimpleDateFormat(pattern, Locale.FRENCH).format(Date(ms))

    /** "16 oct. à 14:30" */
    fun dayMonthTime(ms: Long): String = format("d MMM 'à' HH:mm", ms)

    /** "24/10" (audio retention notices). */
    fun dayMonth(ms: Long): String = format("dd/MM", ms)

    /** "16 oct. 2026 à 14:30" */
    fun fullDateTime(ms: Long): String = format("d MMM yyyy 'à' HH:mm", ms)

    /** "Aujourd’hui à 17:42", "Hier à 16:20", otherwise "14 oct. à 09:30". */
    fun relativeDayTime(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
        val time = format("HH:mm", ms)
        return when (daysBetween(ms, nowMs)) {
            0 -> "Aujourd’hui à $time"
            1 -> "Hier à $time"
            else -> dayMonthTime(ms)
        }
    }

    /** "45 min", "< 1 min"; null when the session has no start/end. */
    fun minutes(startedAt: Long?, endedAt: Long?): String? {
        if (startedAt == null || endedAt == null) return null
        val mins = (endedAt - startedAt) / 60_000L
        return if (mins >= 1) "$mins min" else "< 1 min"
    }

    /** Short dictation length: "1 min 12", "0 min 40"; null without start/end. */
    fun minutesSeconds(startedAt: Long?, endedAt: Long?): String? {
        if (startedAt == null || endedAt == null) return null
        val total = ((endedAt - startedAt) / 1000L).coerceAtLeast(0)
        return "%d min %02d".format(Locale.FRENCH, total / 60, total % 60)
    }

    /** Recording timer: "12:47", "1:02:03". */
    fun timer(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return if (s >= 3600) {
            "%d:%02d:%02d".format(Locale.FRENCH, s / 3600, (s % 3600) / 60, s % 60)
        } else {
            "%02d:%02d".format(Locale.FRENCH, s / 60, s % 60)
        }
    }

    /** "1 enregistrée" / "3 enregistrées" (meetings), "1 enregistré" / "3 enregistrés" (vocaux). */
    fun savedCount(count: Int, feminine: Boolean): String {
        val base = if (feminine) "enregistrée" else "enregistré"
        return if (count > 1) "$count ${base}s" else "$count $base"
    }

    private fun daysBetween(ms: Long, nowMs: Long): Int {
        fun dayStart(t: Long) = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diff = dayStart(nowMs) - dayStart(ms)
        // Round to absorb DST shifts (23 h / 25 h days).
        return Math.round(diff / 86_400_000.0).toInt()
    }
}
