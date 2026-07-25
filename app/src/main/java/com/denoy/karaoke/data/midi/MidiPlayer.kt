package com.denoy.karaoke.data.midi

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile

class MidiPlayer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var soundFontId: Int = 0

    // MIDI channel state
    private val activeNotes = mutableMapOf<Int, MutableSet<Int>>() // channel -> notes
    private val channelVolumes = IntArray(16) { 100 }
    private val channelPrograms = IntArray(16) { 0 }
    private var currentTempo = 500000 // microseconds per quarter note (120 BPM)
    private var ticksPerBeat = 480

    interface Callback {
        fun onTick(tick: Int)
        fun onLyricTick(tick: Int, text: String)
        fun onSongEnd()
    }

    fun loadSoundFont(soundFontFile: File): Boolean {
        return try {
            // Use SoundPool with the SoundFont for MIDI rendering
            soundPool = SoundPool.Builder()
                .setMaxStreams(32)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()

            soundFontId = soundPool?.load(soundFontFile.path, 1) ?: 0
            soundFontId > 0
        } catch (e: Exception) {
            false
        }
    }

    fun play(midiData: ByteArray, callback: Callback) {
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            parseAndPlay(midiData, callback)
        }
    }

    fun stop() {
        playbackJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        soundPool?.autoPause()
    }

    fun release() {
        stop()
        soundPool?.release()
        soundPool = null
    }

    private suspend fun parseAndPlay(midiData: ByteArray, callback: Callback) {
        try {
            val parser = MidiParser()
            val midiFile = parser.parse(midiData)

            // Setup audio output
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            // Parse and play track events
            var currentTick = 0
            for (track in midiFile.tracks) {
                var tick = 0
                for (event in track.events) {
                    tick += event.deltaTime
                    currentTick = tick

                    when (event.type) {
                        MidiParser.EventType.NOTE_ON -> {
                            callback.onTick(tick)
                            callback.onLyricTick(tick, "")
                            noteOn(event.channel, event.note, event.velocity)
                        }
                        MidiParser.EventType.NOTE_OFF -> {
                            noteOff(event.channel, event.note)
                        }
                        MidiParser.EventType.TEMPO -> {
                            currentTempo = event.tempo
                        }
                        MidiParser.EventType.LYRIC -> {
                            callback.onLyricTick(tick, event.text)
                        }
                        MidiParser.EventType.TEXT -> {}
                        MidiParser.EventType.PROGRAM_CHANGE -> {
                            if (event.channel < 16) {
                                channelPrograms[event.channel] = event.program
                            }
                        }
                        MidiParser.EventType.POLY_PRESSURE -> {}
                        MidiParser.EventType.CONTROL_CHANGE -> {}
                        MidiParser.EventType.CHANNEL_PRESSURE -> {}
                        MidiParser.EventType.PITCH_BEND -> {}
                        MidiParser.EventType.META -> {}
                        MidiParser.EventType.UNKNOWN -> {}
                    }
                }
            }

            callback.onSongEnd()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun noteOn(channel: Int, note: Int, velocity: Int) {
        activeNotes.getOrPut(channel) { mutableSetOf() }.add(note)
        // Play through SoundPool with appropriate pitch/sample
        soundPool?.play(soundFontId, velocity / 127f, velocity / 127f, 1, 0, getNoteFrequency(note) / 440f)
    }

    private fun noteOff(channel: Int, note: Int) {
        activeNotes[channel]?.remove(note)
    }

    private fun getNoteFrequency(note: Int): Float {
        return 440f * Math.pow(2.0, (note - 69) / 12.0).toFloat()
    }
}
