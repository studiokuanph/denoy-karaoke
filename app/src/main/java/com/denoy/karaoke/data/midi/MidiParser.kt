package com.denoy.karaoke.data.midi

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MidiParser {

    enum class EventType {
        NOTE_OFF, NOTE_ON, POLY_PRESSURE, CONTROL_CHANGE,
        PROGRAM_CHANGE, CHANNEL_PRESSURE, PITCH_BEND,
        TEMPO, TEXT, LYRIC, META, UNKNOWN
    }

    data class MidiEvent(
        val deltaTime: Int,
        val type: EventType,
        val channel: Int = 0,
        val note: Int = 0,
        val velocity: Int = 0,
        val program: Int = 0,
        val tempo: Int = 500000,
        val text: String = "",
        val rawType: Int = 0,
        val data: ByteArray = byteArrayOf()
    )

    data class MidiTrack(val events: List<MidiEvent>)

    data class MidiFile(
        val format: Int,
        val numTracks: Int,
        val ticksPerBeat: Int,
        val tracks: List<MidiTrack>
    )

    fun parse(data: ByteArray): MidiFile {
        val stream = ByteArrayInputStream(data)

        // Read MIDI header
        val header = readBytes(stream, 4)
        require(String(header) == "MThd") { "Not a valid MIDI file: missing MThd header" }

        val headerLen = readInt32(stream)
        val format = readInt16(stream)
        val numTracks = readInt16(stream)
        val ticksPerBeat = readInt16(stream)

        val tracks = mutableListOf<MidiTrack>()

        for (t in 0 until numTracks) {
            tracks.add(readTrack(stream))
        }

        return MidiFile(format, numTracks, ticksPerBeat, tracks)
    }

    private fun readTrack(stream: ByteArrayInputStream): MidiTrack {
        val chunkType = readBytes(stream, 4)
        require(String(chunkType) == "MTrk") { "Expected MTrk" }

        val trackLen = readInt32(stream)
        val trackData = readBytes(stream, trackLen)
        val trackStream = ByteArrayInputStream(trackData)

        val events = mutableListOf<MidiEvent>()
        var lastStatus = 0
        var tick = 0

        while (trackStream.available() > 0) {
            val deltaTime = readVarLen(trackStream)
            var statusByte = trackStream.read()

            if (statusByte < 0) break

            // Running status
            if (statusByte and 0x80 == 0) {
                statusByte = lastStatus
                trackStream.reset()
                trackStream.skip((-1).toLong())
            }

            lastStatus = statusByte

                when {
                    statusByte == 0xFF -> { // Meta event
                        val metaType = trackStream.read()
                        val metaLen = readVarLen(trackStream)
                        val metaData = readBytes(trackStream, metaLen)

                    when (metaType) {
                        0x51 -> { // Tempo
                            val tempo = ByteBuffer.wrap(metaData)
                                .order(ByteOrder.BIG_ENDIAN).getInt() and 0xFFFFFF
                            events.add(MidiEvent(deltaTime, EventType.TEMPO, tempo = tempo))
                        }
                        0x01 -> { // Text
                            events.add(MidiEvent(deltaTime, EventType.TEXT,
                                text = String(metaData)))
                        }
                        0x05 -> { // Lyric
                            events.add(MidiEvent(deltaTime, EventType.LYRIC,
                                text = String(metaData)))
                        }
                        0x2F -> { // End of Track
                            break
                        }
                        else -> {
                            events.add(MidiEvent(deltaTime, EventType.META,
                                rawType = metaType, data = metaData))
                        }
                    }
                }
                statusByte in 0x80..0x8F -> { // Note Off
                    val channel = statusByte and 0x0F
                    val note = trackStream.read()
                    val velocity = trackStream.read()
                    events.add(MidiEvent(deltaTime, EventType.NOTE_OFF,
                        channel = channel, note = note, velocity = velocity))
                }
                statusByte in 0x90..0x9F -> { // Note On
                    val channel = statusByte and 0x0F
                    val note = trackStream.read()
                    val velocity = trackStream.read()
                    events.add(MidiEvent(deltaTime, EventType.NOTE_ON,
                        channel = channel, note = note, velocity = velocity))
                }
                statusByte in 0xC0..0xCF -> { // Program Change
                    val channel = statusByte and 0x0F
                    val program = trackStream.read()
                    events.add(MidiEvent(deltaTime, EventType.PROGRAM_CHANGE,
                        channel = channel, program = program))
                }
                statusByte in 0xB0..0xBF -> { // Control Change
                    val channel = statusByte and 0x0F
                    val controller = trackStream.read()
                    val value = trackStream.read()
                    events.add(MidiEvent(deltaTime, EventType.CONTROL_CHANGE,
                        channel = channel, rawType = controller))
                }
                else -> {
                    // Skip unknown events
                }
            }
        }

        return MidiTrack(events)
    }

    private fun readBytes(stream: ByteArrayInputStream, count: Int): ByteArray {
        val data = ByteArray(count)
        stream.read(data)
        return data
    }

    private fun readInt16(stream: ByteArrayInputStream): Int {
        val b1 = stream.read()
        val b2 = stream.read()
        return (b1 shl 8) or b2
    }

    private fun readInt32(stream: ByteArrayInputStream): Int {
        val b1 = stream.read()
        val b2 = stream.read()
        val b3 = stream.read()
        val b4 = stream.read()
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    private fun readVarLen(stream: ByteArrayInputStream): Int {
        var value = 0
        var byte: Int
        do {
            byte = stream.read()
            value = (value shl 7) or (byte and 0x7F)
        } while (byte and 0x80 != 0)
        return value
    }
}
