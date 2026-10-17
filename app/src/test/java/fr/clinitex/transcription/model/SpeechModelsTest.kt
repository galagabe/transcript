package fr.clinitex.transcription.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelsTest {

    @Test
    fun `catalog has unique ids`() {
        val ids = SpeechModels.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `catalog contains only the multilingual v3 transducer`() {
        assertEquals(listOf(SpeechModels.PARAKEET_V3), SpeechModels.all)
        val v3 = SpeechModels.PARAKEET_V3
        assertEquals(SpeechModelKind.NEMO_TRANSDUCER, v3.kind)
        assertTrue(v3.files.any { it.name == "encoder.int8.onnx" })
        assertTrue(v3.files.all { it.url.startsWith("https://") })
        assertFalse(v3.packagedAsTarBz2)
    }

    @Test
    fun `expectedFiles are the downloaded files for plain models`() {
        assertEquals(
            SpeechModels.PARAKEET_V3.files.map { it.name },
            SpeechModels.PARAKEET_V3.expectedFiles,
        )
    }

    @Test
    fun `byId falls back to v3 for unknown, empty or removed ids`() {
        assertEquals(SpeechModels.PARAKEET_V3, SpeechModels.byId(null))
        assertEquals(SpeechModels.PARAKEET_V3, SpeechModels.byId(""))
        assertEquals(SpeechModels.PARAKEET_V3, SpeechModels.byId("no-such-model"))
        // Former English-only 110M id, possibly still stored in preferences.
        assertEquals(SpeechModels.PARAKEET_V3, SpeechModels.byId("parakeet-tdt-ctc-110m-en-36000-int8"))
    }

    @Test
    fun `default id resolves to a catalog entry`() {
        assertTrue(SpeechModels.all.any { it.id == SpeechModels.DEFAULT_ID })
    }
}
