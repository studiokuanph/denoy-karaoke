package com.denoy.karaoke.ui.remote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.denoy.karaoke.R
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class RemoteFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var tvLyrics: TextView

    private var wsClient: WebSocketClient? = null
    private var reconnectJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        var serverHost: String = "192.168.254.198"
        var serverPort: Int = 8765
        var onSongLoaded: ((String, String) -> Unit)? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_remote, container, false)
        tvStatus = view.findViewById(R.id.tv_remote_status)
        tvIpAddress = view.findViewById(R.id.tv_remote_ip)
        tvNowPlaying = view.findViewById(R.id.tv_now_playing)
        tvLyrics = view.findViewById(R.id.tv_lyrics)
        return view
    }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        super.onViewCreated(view, savedState)
        connect()
    }

    fun connect(host: String = serverHost, port: Int = serverPort) {
        serverHost = host
        serverPort = port
        scope.launch {
            tvStatus.text = "Connecting to $host:$port..."
            connectToServer(host, port)
        }
    }

    private fun connectToServer(host: String, port: Int) {
        try {
            val uri = URI.create("ws://$host:$port/")
            wsClient = object : WebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake) {
                    scope.launch {
                        tvStatus.text = "Connected to PC server"
                        tvIpAddress.text = "$host:$port"
                    }
                }

                override fun onMessage(message: String) {
                    try {
                        val data = org.json.JSONObject(message)
                        when {
                            data.has("type") && data.getString("type") == "state_update" -> {
                                val np = data.optJSONObject("now_playing")
                                if (np != null) {
                                    val title = np.optString("title", "")
                                    val code = np.optString("code", "")
                                    scope.launch {
                                        tvNowPlaying.text = if (title.isNotEmpty()) "$title (Code: $code)" else "No song playing"
                                    }
                                }
                            }
                            data.has("type") && data.getString("type") == "SONG_LOADED" -> {
                                val title = data.optJSONObject("now_playing")?.optString("title", "") ?: ""
                                val lines = data.optJSONArray("lines")
                                val lyrics = StringBuilder()
                                if (lines != null) {
                                    for (i in 0 until lines.length()) {
                                        val line = lines.getJSONObject(i)
                                        val speaker = line.optString("speaker", "")
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
                                    tvLyrics.text = lyrics.toString()
                                }
                            }
                            data.has("type") && data.getString("type") == "welcome" -> {
                                scope.launch {
                                    tvStatus.text = "Connected to ${data.optString("server", "unknown")}"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        scope.launch { tvStatus.text = "Parse error: ${e.message}" }
                    }
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    scope.launch {
                        tvStatus.text = "Disconnected. Reconnecting..."
                        // Auto-reconnect
                        delay(3000)
                        connectToServer(host, port)
                    }
                }

                override fun onError(ex: Exception) {
                    scope.launch {
                        tvStatus.text = "Error: ${ex.message}"
                    }
                }
            }
            wsClient?.connect()
        } catch (e: Exception) {
            scope.launch {
                tvStatus.text = "Failed: ${e.message}"
            }
        }
    }

    fun playSong(code: String) {
        val msg = """{"type":"command","action":"play","code":"$code"}"""
        wsClient?.send(msg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wsClient?.close()
        scope.cancel()
    }
}
