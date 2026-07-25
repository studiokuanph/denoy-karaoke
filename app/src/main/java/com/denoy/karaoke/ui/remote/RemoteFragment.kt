package com.denoy.karaoke.ui.remote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.denoy.karaoke.R
import kotlinx.coroutines.*
import java.net.ServerSocket

class RemoteFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private var serverJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_remote, container, false)
        tvStatus = view.findViewById(R.id.tv_remote_status)
        tvIpAddress = view.findViewById(R.id.tv_remote_ip)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startRemoteServer()
    }

    private fun startRemoteServer() {
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = ServerSocket(48080)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Server running on port 48080"
                    tvIpAddress.text = getLocalIpAddress()
                }

                while (isActive) {
                    val client = server.accept()
                    launch {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Server error: ${e.message}"
                }
            }
        }
    }

    private fun handleClient(client: java.net.Socket) {
        try {
            val reader = client.getInputStream().bufferedReader()
            val writer = client.getOutputStream().bufferedWriter()

            writer.write("HTTP/1.1 200 OK\r\n")
            writer.write("Content-Type: application/json\r\n\r\n")
            writer.write("{\"status\":\"ok\",\"server\":\"Denoy Karaoke\"}")
            writer.flush()

            client.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun stopRemoteServer() {
        serverJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRemoteServer()
    }
}
