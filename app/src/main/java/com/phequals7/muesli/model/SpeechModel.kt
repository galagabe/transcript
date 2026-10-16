package com.phequals7.muesli.model

import android.content.Context
import com.phequals7.muesli.data.SharedStore

/** Decoder architecture of an on-device ASR model (drives the sherpa-onnx config branch). */
enum class SpeechModelKind { NEMO_TRANSDUCER, NEMO_CTC }

/**
 * One downloadable on-device speech model.
 *
 * @param packagedAsTarBz2 when true the single file in [files] is a .tar.bz2
 *   archive (GitHub release distribution) that is extracted after download;
 *   [requiredOutputs] lists the extracted files that must exist.
 */
data class SpeechModel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val capabilityLabel: String,
    val detail: String,
    val kind: SpeechModelKind,
    val files: List<ModelManager.ModelFile>,
    val packagedAsTarBz2: Boolean = false,
    val requiredOutputs: List<String> = emptyList(),
) {
    val totalSizeBytes: Long get() = files.sumOf { it.sizeBytes }

    /** Files that must exist for the model to be considered usable. */
    val expectedFiles: List<String>
        get() = if (packagedAsTarBz2) requiredOutputs else files.map { it.name }
}

/**
 * Catalog of on-device ASR models. Clinitex keeps only the multilingual
 * Parakeet v3 (the English-only 110M model cannot transcribe French).
 */
object SpeechModels {

    /** iOS parity: Parakeet v3 600M multilingual (FluidAudio parakeet-v3). */
    val PARAKEET_V3 = SpeechModel(
        id = "parakeet-tdt-0.6b-v3-int8",
        displayName = "Parakeet v3 600M",
        shortName = "Parakeet v3",
        capabilityLabel = "Multilingue · 25 langues dont le français",
        detail = "Modèle de reconnaissance vocale multilingue, exécuté entièrement sur le téléphone. Le français impose ce modèle.",
        kind = SpeechModelKind.NEMO_TRANSDUCER,
        files = listOf(
            ModelManager.ModelFile(
                "tokens.txt",
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/tokens.txt",
                103_936L,
            ),
            ModelManager.ModelFile(
                "joiner.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/joiner.int8.onnx",
                6_000_000L,
            ),
            ModelManager.ModelFile(
                "decoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/decoder.int8.onnx",
                11_000_000L,
            ),
            ModelManager.ModelFile(
                "encoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/encoder.int8.onnx",
                651_000_000L,
            ),
        ),
    )

    val all: List<SpeechModel> = listOf(PARAKEET_V3)

    /** Existing installs already have v3 downloaded, so it stays the default. */
    const val DEFAULT_ID = "parakeet-tdt-0.6b-v3-int8"

    fun byId(id: String?): SpeechModel = all.firstOrNull { it.id == id } ?: PARAKEET_V3

    fun selected(context: Context): SpeechModel =
        byId(SharedStore(context.applicationContext).selectedModelId)
}
