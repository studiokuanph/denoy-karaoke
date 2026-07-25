package com.denoy.karaoke.ui.remote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.denoy.karaoke.R
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class RemoteFragment : Fragment() {

    private lateinit var etIp: TextView
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var tvLyrics: TextView

    private var wsClient: WebSocketClient? = null
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        var instance: RemoteFragment? = null
        var serverHost: String = ""
        var serverPort: Int = 8765
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_remote, container, false)
        etIp = view.findViewById(R.id.et_server_ip)
        btnConnect = view.findViewById(R.id.btn_connect)
        tvStatus = view.findViewById(R.id.tv_remote_status)
        tvNowPlaying = view.findViewById(R.id.tv_now_playing)
        tvLyrics = view.findViewById(R.id.tv_lyrics)

        instance = this

        btnConnect.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isNotEmpty()) connect(ip, serverPort)
        }

        return view
    }

    fun connect(host: String, port: Int) {
        serverHost = host
        serverPort = port
        wsClient?.close()
        tvStatus.text = "Connecting to $host:$port..."
        tvStatus.setTextColor(0xFFFFAA00.toInt())

        try {
            val uri = URI.create("ws://$host:$port/")
            wsClient = object : WebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake) {
                    scope.launch {
                        tvStatus.text = "Connected to PC!"
                        tvStatus.setTextColor(0xFF4CAF50.toInt())
                    }
                }

                override fun onMessage(message: String) {
                    handleMessage(message)
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    scope.launch {
                        tvStatus.text = "Disconnected"
                        tvStatus.setTextColor(0xFFFF5252.toInt())
                        tvNowPlaying.visibility = View.GONE
                        tvLyrics.visibility = View.GONE
                    }
                }

                override fun onError(ex: Exception) {
                    scope.launch {
                        tvStatus.text = "Connection failed: ${ex.message}"
                        tvStatus.setTextColor(0xFFFF5252.toInt())
                    }
                }
            }
            wsClient?.connect()
        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.message}"
            tvStatus.setTextColor(0xFFFF5252.toInt())
        }
    }

    private fun handleMessage(message: String) {
        try {
            val data = org.json.JSONObject(message)
            when {
                data.has("type") && data.getString("type") == "state_update" -> {
                    val np = data.optJSONObject("now_playing")
                    if (np != null) {
                        val title = np.optString("title", "")
                        scope.launch {
                            if (title.isNotEmpty()) {
                                tvNowPlaying.text = "Now Playing: $title"
                                tvNowPlaying.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                data.has("type") && data.getString("type") == "SONG_LOADED" -> {
                    val np = data.optJSONObject("now_playing")
                    val title = np?.optString("title", "") ?: ""
                    val lines = data.optJSONArray("lines")
                    val lyrics = StringBuilder()
                    if (lines != null) {
                        for (i in 0 until lines.length()) {
                            val line = lines.getJSONObject(i)
                            val syllables = line.optJSONArray("syllables")
                            if (syllables != null) {
                                for (j in 0 until syllables.length()) {
                                    lyrics.append(syllables.getJSONObject(j).optString("text", ""))
                                }
                                lyrics.append("\n")
                            }
                        }
                    }
                    scope.launch {
                        tvNowPlaying.text = "Now Playing: $title"
                        tvNowPlaying.visibility = View.VISIBLE
                        if (lyrics.isNotEmpty()) {
                            tvLyrics.text = lyrics.toString()
                            tvLyrics.visibility = View.VISIBLE
                        }
                    }
                }
                data.has("type") && data.getString("type") == "welcome" -> {
                    scope.launch {
                        tvStatus.text = "Connected to ${data.optString("server", "PC")}"
                        tvStatus.setTextColor(0xFF4CAF50.toInt())
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun playSong(code: String) {
        val msg = """{"type":"command","action":"play","code":"$code"}"""
        wsClient?.send(msg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wsClient?.close()
        scope.cancel()
        instance = null
    }
}
