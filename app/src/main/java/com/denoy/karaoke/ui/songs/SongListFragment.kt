package com.denoy.karaoke.ui.songs

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.denoy.karaoke.R
import com.denoy.karaoke.data.model.SongEntry
import com.denoy.karaoke.data.parser.IdxParser
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class SongListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: TextView
    private lateinit var btnAction: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var songEntries: List<SongEntry> = emptyList()
    private var adapter: SongAdapter? = null
    private var datFile: File? = null
    private var downloadId: Long = -1L
    private var downloadJob: Job? = null
    private var downloadCompleteReceiver: BroadcastReceiver? = null

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) copyDatFromFolder(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_songs, container, false)
        recyclerView = view.findViewById(R.id.song_list)
        searchInput = view.findViewById(R.id.search_input)
        btnAction = view.findViewById(R.id.btn_load)
        tvStatus = view.findViewById(R.id.tv_status)
        progressBar = view.findViewById(R.id.progress_bar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SongAdapter(emptyList()) { entry ->
            val bundle = Bundle().apply {
                putString("title", entry.title)
                putLong("offset", entry.datOffset)
                putInt("size", entry.compressedSize)
                putInt("songId", entry.songId)
            }
            parentFragmentManager.setFragmentResult("play_song", bundle)
            // Try sending to PC bridge if connected
            try {
                com.denoy.karaoke.ui.remote.RemoteFragment.instance?.playSong(entry.songId.toString())
            } catch (_: Exception) {}
            findNavController().navigate(R.id.nav_player)
        }
        recyclerView.adapter = adapter

        searchInput.setOnEditorActionListener { _, _, _ ->
            filterSongs(searchInput.text.toString())
            true
        }

        loadBundledIndex()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        downloadJob?.cancel()
        downloadCompleteReceiver?.let { requireContext().unregisterReceiver(it) }
    }

    // ========== INDEX LOADING ==========

    private fun loadBundledIndex() {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Loading song index..."

        Thread {
            try {
                val idxFile = File(requireContext().cacheDir, "songfile.idx")
                if (!idxFile.exists()) {
                    requireContext().assets.open("songfile.idx").use { input ->
                        FileOutputStream(idxFile).use { output -> input.copyTo(output) }
                    }
                }

                val parser = IdxParser(idxFile.absolutePath)
                val db = parser.parse()
                songEntries = db.entries
                datFile = findExistingDat()
                AppSettings.idxPath = idxFile.absolutePath

                requireActivity().runOnUiThread {
                    adapter?.updateEntries(songEntries)
                    updateStatus()
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: e.javaClass.simpleName
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    tvStatus.text = "Error loading index: $errMsg"
                    btnAction.text = "Browse for .idx file"
                    btnAction.setOnClickListener { pickFolder() }
                }
            }
        }.start()
    }

    // ========== DAT FILE DETECTION ==========

    private fun findExistingDat(): File? {
        val candidates = listOf(
            File(requireContext().getExternalFilesDir(null), "songfile.dat"),
            File(requireContext().filesDir, "songfile.dat"),
            File(Environment.getExternalStorageDirectory(), "songfile.dat"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "songfile.dat"),
            File("/sdcard/songfilepack/songfile.dat"),
            AppSettings.datPath.ifEmpty { null }?.let { File(it) }
        ).filterNotNull()

        for (f in candidates) {
            if (f.exists() && f.length() > 1_000_000L) {
                AppSettings.datPath = f.absolutePath
                AppSettings.savedDatPath = f.absolutePath
                return f
            }
        }
        return null
    }

    private fun updateStatus() {
        val dat = datFile
        if (dat != null && dat.exists() && dat.length() > 1_000_000L) {
            AppSettings.datPath = dat.absolutePath
            val mb = dat.length() / (1024 * 1024)
            tvStatus.text = "${songEntries.size} songs ready | Data file: ${mb}MB"
            btnAction.text = "Re-select .dat file"
            btnAction.setOnClickListener { showDatOptions() }
            progressBar.visibility = View.GONE
        } else {
            tvStatus.text = "${songEntries.size} songs loaded | Need songfile.dat (568MB)"
            btnAction.text = "Download songfile.dat"
            btnAction.setOnClickListener { startAutoDownload() }
            progressBar.visibility = View.GONE
        }
    }

    // ========== AUTO DOWNLOAD ==========

    private fun startAutoDownload() {
        val url = AppSettings.downloadUrl.ifEmpty {
            "https://github.com/studiokuanph/denoy-karaoke/releases/download/v1.0.0/songfile.dat"
        }
        AppSettings.downloadUrl = url

        tvStatus.text = "Starting download..."
        btnAction.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressBar.max = 100

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val destDir = requireContext().getExternalFilesDir(null)
                    ?: requireContext().filesDir
                destDir.mkdirs()
                val destFile = File(destDir, "songfile.dat")

                // Remove partial downloads
                if (destFile.exists()) destFile.delete()

                val manager = requireContext()
                    .getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle("Denoy Karaoke - songfile.dat")
                    .setDescription("Downloading song database (568MB)")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationUri(Uri.fromFile(destFile))
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                downloadId = manager.enqueue(request)

                // Register completion receiver
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            onDownloadFinished(destFile)
                        }
                    }
                }
                downloadCompleteReceiver = receiver
                requireContext().registerReceiver(
                    receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )

                // Poll for progress
                while (isActive) {
                    val cursor: Cursor = manager.query(
                        DownloadManager.Query().setFilterById(downloadId)
                    )
                    if (cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getLong(
                            cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        val totalBytes = cursor.getLong(
                            cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val status = cursor.getInt(
                            cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        )

                        if (totalBytes > 0) {
                            val pct = (bytesDownloaded * 100 / totalBytes).toInt()
                            val mb = bytesDownloaded / (1024 * 1024)
                            val totalMb = totalBytes / (1024 * 1024)
                            withContext(Dispatchers.Main) {
                                progressBar.progress = pct
                                tvStatus.text = "Downloading: $mb / $totalMb MB ($pct%)"
                                btnAction.text = "Downloading..."
                            }
                        }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                cursor.close()
                                break
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(
                                    cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                )
                                withContext(Dispatchers.Main) {
                                    tvStatus.text = "Download failed (code $reason)"
                                    btnAction.isEnabled = true
                                    btnAction.text = "Retry download"
                                    btnAction.setOnClickListener { startAutoDownload() }
                                    progressBar.visibility = View.GONE
                                }
                                cursor.close()
                                break
                            }
                        }
                    }
                    cursor.close()
                    delay(2000)
                }

            } catch (e: Exception) {
                val errMsg = e.message ?: e.javaClass.simpleName
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Download error: $errMsg"
                    btnAction.isEnabled = true
                    btnAction.text = "Retry download"
                    btnAction.setOnClickListener { startAutoDownload() }
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun onDownloadFinished(destFile: File) {
        downloadJob?.cancel()
        requireActivity().runOnUiThread {
            if (destFile.exists() && destFile.length() > 1_000_000L) {
                datFile = destFile
                AppSettings.datPath = destFile.absolutePath
                AppSettings.savedDatPath = destFile.absolutePath
                progressBar.progress = 100
                tvStatus.text = "Download complete! ${songEntries.size} songs ready"
                btnAction.text = "Re-select .dat file"
                btnAction.isEnabled = true
                btnAction.setOnClickListener { showDatOptions() }
            } else {
                tvStatus.text = "Download failed - file not found or too small"
                btnAction.isEnabled = true
                btnAction.text = "Retry download"
                btnAction.setOnClickListener { startAutoDownload() }
            }
        }
    }

    // ========== MANUAL DOWNLOAD / FOLDER PICKER ==========

    private fun showDatOptions() {
        val options = arrayOf("Download from GitHub", "Browse folder on device", "Cancel")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Get songfile.dat")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startAutoDownload()
                    1 -> pickFolder()
                }
            }
            .show()
    }

    private fun copyDatFromFolder(folderUri: Uri) {
        tvStatus.text = "Copying songfile.dat..."
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        Thread {
            try {
                val docTree = DocumentFile.fromTreeUri(requireContext(), folderUri)
                    ?: throw Exception("Cannot access folder")
                for (child in docTree.listFiles()) {
                    val name = child.name?.lowercase() ?: continue
                    if (name.endsWith(".dat")) {
                        val dest = File(
                            requireContext().getExternalFilesDir(null), "songfile.dat"
                        )
                        requireContext().contentResolver.openInputStream(child.uri)
                            ?.use { input ->
                                FileOutputStream(dest).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        datFile = dest
                        AppSettings.datPath = dest.absolutePath
                        AppSettings.savedDatPath = dest.absolutePath
                        break
                    }
                }
                requireActivity().runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.visibility = View.GONE
                    updateStatus()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Error: ${e.message}"
                    btnAction.isEnabled = true
                    btnAction.text = "Try again"
                    btnAction.setOnClickListener { showDatOptions() }
                }
            }
        }.start()
    }

    private fun pickFolder() {
        try {
            pickFolderLauncher.launch(null)
        } catch (e: Exception) {
            tvStatus.text = "Folder picker error: ${e.message}"
        }
    }

    private fun filterSongs(query: String) {
        val filtered = if (query.isBlank()) songEntries
        else songEntries.filter { it.title.contains(query, ignoreCase = true) }
        adapter?.updateEntries(filtered)
    }
}

object AppSettings {
    var datPath: String = ""
    var idxPath: String = ""
    var savedDatPath: String? = null
    var downloadUrl: String = ""
}
