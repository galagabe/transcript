package fr.clinitex.transcription.ui.notes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import fr.clinitex.transcription.meetings.MetreCsv
import java.io.File

/**
 * Shares the CSV files of a Métré (premises for the costing tool, and the
 * sanitary equipment for the supplier when some was dictated) in one go:
 * one mail or message carries both files.
 */
object MetreExport {

    /** True when [notesMarkdown] holds premises to export. */
    fun canExport(notesMarkdown: String): Boolean = MetreCsv.rows(notesMarkdown).isNotEmpty()

    fun share(context: Context, title: String, dateMs: Long, notesMarkdown: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val files = buildList {
            val rows = MetreCsv.rows(notesMarkdown)
            if (rows.isNotEmpty()) {
                add(File(dir, MetreCsv.fileName(title, dateMs)).apply { writeText(MetreCsv.toCsv(rows), Charsets.UTF_8) })
            }
            val equipment = MetreCsv.equipmentRows(notesMarkdown)
            if (equipment.isNotEmpty()) {
                add(
                    File(dir, MetreCsv.equipmentFileName(title, dateMs))
                        .apply { writeText(MetreCsv.toCsv(equipment, MetreCsv.EQUIPMENT_COLUMNS), Charsets.UTF_8) }
                )
            }
        }
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }.apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(files.first().name, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exporter le métré (CSV)"))
    }
}
