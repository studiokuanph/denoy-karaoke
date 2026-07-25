package com.denoy.karaoke.data.model

data class MidiSong(
    val midiData: ByteArray,
    val tempoMap: List<TempoEntry>,
    val lyrics: List<LyricLine>
) {
    data class TempoEntry(val tick: Int, val tempo: Int)
    data class LyricLine(
        val speaker: String,
        val syllables: List<Syllable>
    )
    data class Syllable(
        val text: String,
        val startTick: Int,
        val endTick: Int
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MidiSong) return false
        return midiData.contentEquals(other.midiData)
    }

    override fun hashCode(): Int = midiData.contentHashCode()
}
