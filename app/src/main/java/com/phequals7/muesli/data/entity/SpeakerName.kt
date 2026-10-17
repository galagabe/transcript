package com.phequals7.muesli.data.entity

import androidx.room.Entity

/** Name given by the user to "Intervenant [speaker]" of one meeting (lot 3). */
@Entity(tableName = "speaker_names", primaryKeys = ["sessionId", "speaker"])
data class SpeakerName(
    val sessionId: String,
    val speaker: Int,
    val name: String,
)
