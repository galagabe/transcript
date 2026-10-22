package fr.clinitex.transcription.meetings

import fr.clinitex.transcription.summaries.ClinitexSummaryClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import kotlin.time.Duration.Companion.minutes

/**
 * Live check of the Métré template against the real n8n summary webhook
 * (Claude): a dictated premises list must come back as a table that
 * [MetreCsv] exports. Ignored unless CLINDY_METRE_LIVE is set; URL and
 * token come from local.properties (never printed). The report goes to
 * CLINDY_METRE_OUT when set.
 */
class MetreWebhookTest {

    private fun localProperty(name: String): String {
        val file = listOf(File("local.properties"), File("../local.properties")).first { it.exists() }
        return Properties().apply { file.inputStream().use { load(it) } }.getProperty(name, "")
    }

    @Test
    fun `a dictated métré comes back as an exportable table`() = runTest(timeout = 5.minutes) {
        assumeTrue("CLINDY_METRE_LIVE not set", System.getenv("CLINDY_METRE_LIVE") != null)
        val client = ClinitexSummaryClient(localProperty("clinitex.summaryUrl"), localProperty("clinitex.deviceToken"))
        assumeTrue(client.isConfigured())

        val transcript = """
            Bon alors métré pour le site de la zone d'activité. Bâtiment A, rez-de-chaussée. Trois bureaux de 12 mètres carrés,
            moquette, passage deux fois par semaine. Ensuite les sanitaires, euh, 8 mètres carrés, carrelage, tous les jours,
            cinq fois par semaine, avec réapprovisionnement des consommables. Le couloir, thermoplastique, 35 mètres carrés,
            deux fois par semaine aussi. Premier étage : une salle de réunion de 40 mètres carrés en parquet bois, une fois par
            semaine. Le réfectoire, 25 mètres carrés, carrelage, cinq fois par semaine. Ah et le local technique au sous-sol,
            sol en ciment, on sait pas la surface. Accès par badge, le client veut un passage avant 8 heures.
        """.trimIndent().replace("\n", " ")

        val response = client.summarize(
            ClinitexSummaryClient.SummaryRequest(
                sessionId = "test-metre",
                title = "Test métré",
                templateId = MeetingTemplate.METRE.id,
                templateInstructions = MeetingTemplate.METRE.instructions,
                transcript = transcript,
                durationSec = 90,
                appVersion = "test",
            )
        )
        val rows = MetreCsv.rows(response.notes)
        val report = "Titre : ${response.title}\n\n${response.notes}\n\n--- CSV ---\n${MetreCsv.toCsv(rows).removePrefix("﻿")}"
        System.getenv("CLINDY_METRE_OUT")?.let { File(it).writeText(report) }
        println(report)
        assertTrue("no Métré table in the notes", rows.isNotEmpty())
    }
}
