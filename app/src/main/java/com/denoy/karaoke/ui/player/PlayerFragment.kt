package com.denoy.karaoke.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.denoy.karaoke.R
import com.denoy.karaoke.data.crypto.DatDecoder
import com.denoy.karaoke.data.midi.MidiPlayer
import kotlinx.coroutines.*

class PlayerFragment : Fragment() {

    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvLyricsTop: TextView
    private lateinit var tvLyricsBottom: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnPlay: View
    private lateinit var btnStop: View

    private var midiPlayer: MidiPlayer? = null
    private var datDecoder: DatDecoder? = null
    private var currentSongTitle = ""
    private var currentSongId = 0
    private var coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_player, container, false)

        tvTitle = view.findViewById(R.id.tv_song_title)
        tvArtist = view.findViewById(R.id.tv_artist)
        tvLyricsTop = view.findViewById(R.id.tv_lyrics_top)
        tvLyricsBottom = view.findViewById(R.id.tv_lyrics_bottom)
        tvTimer = view.findViewById(R.id.tv_timer)
        btnPlay = view.findViewById(R.id.btn_play)
        btnStop = view.findViewById(R.id.btn_stop)

        btnPlay.setOnClickListener { playCurrentSong() }
        btnStop.setOnClickListener { stopPlayback() }

        // Listen for song selection
        parentFragmentManager.setFragmentResultListener("play_song", this) { _, bundle ->
            currentSongTitle = bundle.getString("title", "")
            currentSongId = bundle.getInt("songId", 0)
            val offset = bundle.getLong("offset", 0)
            val size = bundle.getInt("size", 0)
            tvTitle.text = currentSongTitle

            // Load and decode the song
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val midiData = datDecoder?.decode(offset, size)
                    if (midiData != null) {
                        withContext(Dispatchers.Main) {
                            playMidiData(midiData)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvLyricsBottom.text = "Decode failed: ${e.message}"
                    }
                }
            }
        }

        return view
    }

    private fun playCurrentSong() {
        midiPlayer?.play(ByteArray(0), object : MidiPlayer.Callback {
            override fun onTick(tick: Int) {}
            override fun onLyricTick(tick: Int, text: String) {
                requireActivity().runOnUiThread {
                    tvLyricsTop.text = tvLyricsBottom.text
                    tvLyricsBottom.text = text
                }
            }
            override fun onSongEnd() {
                requireActivity().runOnUiThread {
                    tvLyricsBottom.text = "--- End ---"
                }
            }
        })
    }

    private fun stopPlayback() {
        midiPlayer?.stop()
    }

    private fun playMidiData(midiData: ByteArray) {
        if (midiPlayer == null) {
            midiPlayer = MidiPlayer(requireContext())
        }
        playCurrentSong()
    }

    override fun onDestroy() {
        super.onDestroy()
        midiPlayer?.release()
        coroutineScope.cancel()
    }
}
