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
import com.denoy.karaoke.ui.songs.AppSettings
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
    private var currentMidiData: ByteArray? = null
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

        // Listen for song selection from the song list
        parentFragmentManager.setFragmentResultListener("play_song", this) { _, bundle ->
            currentMidiData = null
            val title = bundle.getString("title", "Unknown")
            val offset = bundle.getLong("offset", 0)
            val size = bundle.getInt("size", 0)

            tvTitle.text = title
            tvLyricsTop.text = ""
            tvLyricsBottom.text = "Loading..."

            loadAndPlaySong(offset, size)
        }

        // Also check arguments (direct navigation)
        arguments?.let { args ->
            val title = args.getString("title", "Unknown")
            val offset = args.getLong("offset", 0)
            val size = args.getInt("size", 0)
            if (offset > 0) {
                tvTitle.text = title
                tvLyricsBottom.text = "Loading..."
                loadAndPlaySong(offset, size)
            }
        }

        return view
    }

    private fun loadAndPlaySong(offset: Long, size: Int) {
        val datPath = AppSettings.datPath
        if (datPath.isEmpty()) {
            tvLyricsBottom.text = "No songfile.dat found. Get it from the Songs tab."
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val decoder = DatDecoder(datPath)
                val midiData = decoder.decode(offset, size)

                withContext(Dispatchers.Main) {
                    currentMidiData = midiData
                    tvLyricsBottom.text = "Ready — tap Play"
                    tvTitle.text = tvTitle.text
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errMsg = e.message ?: e.javaClass.simpleName
                    tvLyricsBottom.text = "Decode error: $errMsg"
                }
            }
        }
    }

    private fun playCurrentSong() {
        val data = currentMidiData ?: return

        if (midiPlayer == null) {
            midiPlayer = MidiPlayer(requireContext())
        }

        midiPlayer?.play(data, object : MidiPlayer.Callback {
            override fun onTick(tick: Int) {
                requireActivity().runOnUiThread {
                    tvTimer.text = "Tick: $tick"
                }
            }

            override fun onLyricTick(tick: Int, text: String) {
                requireActivity().runOnUiThread {
                    if (text.isNotBlank()) {
                        tvLyricsTop.text = tvLyricsBottom.text
                        tvLyricsBottom.text = text
                    }
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

    override fun onDestroy() {
        super.onDestroy()
        midiPlayer?.release()
        coroutineScope.cancel()
    }
}
