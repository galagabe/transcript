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
            Bon alors métré pour le site de la zone d'activité. Bâtiment A, rez-de-chaussée. Un bureau accueil de 15 mètres carrés,
            carrelage, tous les jours. Un bureau secrétaire, 10 mètres carrés, moquette, trois fois par semaine. Trois bureaux de 12 mètres carrés,
            moquette, passage deux fois par semaine. Ensuite les sanitaires, euh, 8 mètres carrés, carrelage, tous les jours,
            avec réapprovisionnement des consommables. Dans les sanitaires il faudra deux distributeurs de savon, un distributeur
            d'essuie-mains et un distributeur de papier toilette. Le couloir, thermoplastique, 35 mètres carrés, trois fois par
            semaine. Premier étage : une salle de réunion de 40 mètres carrés en parquet bois, une fois par semaine. Les sanitaires
            du premier, 6 mètres carrés, carrelage, tous les jours, avec un désodorisant. Les vitres du hall, une fois par trimestre.
            Le local archives, 20 mètres carrés, ciment, une fois par mois. Accès par badge, le client veut un passage avant 8 heures.
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
        val equipment = MetreCsv.equipmentRows(response.notes)
        val report = "Titre : ${response.title}\n\n${response.notes}\n\n--- CSV métré ---\n${MetreCsv.toCsv(rows).removePrefix("﻿")}" +
            "\n--- CSV équipements ---\n${MetreCsv.toCsv(equipment, MetreCsv.EQUIPMENT_COLUMNS).removePrefix("﻿")}"
        System.getenv("CLINDY_METRE_OUT")?.let { File(it).writeText(report) }
        println(report)
        assertTrue("no Métré table in the notes", rows.isNotEmpty())
    }
}
