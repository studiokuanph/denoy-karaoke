package com.denoy.karaoke.ui.songs

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.denoy.karaoke.R
import com.denoy.karaoke.data.model.SongEntry
import com.denoy.karaoke.data.parser.IdxParser
import java.io.File
import java.io.FileOutputStream

class SongListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: TextView
    private lateinit var btnAction: Button
    private lateinit var tvStatus: TextView
    private var songEntries: List<SongEntry> = emptyList()
    private var adapter: SongAdapter? = null
    private var datFile: File? = null

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

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SongAdapter(emptyList()) { entry ->
            val bundle = Bundle().apply {
                putString("title", entry.title)
                putLong("offset", entry.datOffset)
                putInt("size", entry.compressedSize)
                putInt("songId", entry.songId)
            }
            parentFragmentManager.setFragmentResult("play_song", bundle)
        }
        recyclerView.adapter = adapter

        searchInput.setOnEditorActionListener { _, _, _ ->
            filterSongs(searchInput.text.toString())
            true
        }

        loadBundledIndex()
        return view
    }

    private fun loadBundledIndex() {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Loading song index..."

        Thread {
            try {
                // Copy bundled .idx from assets to cache
                val idxFile = File(requireContext().cacheDir, "songfile.idx")
                if (!idxFile.exists()) {
                    requireContext().assets.open("songfile.idx").use { input ->
                        FileOutputStream(idxFile).use { output -> input.copyTo(output) }
                    }
                }

                val parser = IdxParser(idxFile.absolutePath)
                val db = parser.parse()
                songEntries = db.entries

                // Check if .dat already exists
                datFile = findExistingDat()

                requireActivity().runOnUiThread {
                    adapter?.updateEntries(songEntries)
                    updateStatus()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    tvStatus.text = "Error loading index: ${e.message}"
                    btnAction.text = "Browse for .idx file"
                    btnAction.setOnClickListener { pickFolder() }
                }
            }
        }.start()
    }

    private fun findExistingDat(): File? {
        val candidates = listOf(
            File(requireContext().getExternalFilesDir(null), "songfile.dat"),
            File(requireContext().filesDir, "songfile.dat"),
            File(Environment.getExternalStorageDirectory(), "songfile.dat"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "songfile.dat"),
            File("/sdcard/songfilepack/songfile.dat"),
            AppSettings.savedDatPath?.let { File(it) }
        ).filterNotNull()

        for (f in candidates) {
            if (f.exists() && f.length() > 1000000L) return f
        }
        return null
    }

    private fun updateStatus() {
        val dat = datFile
        if (dat != null && dat.exists()) {
            AppSettings.datPath = dat.absolutePath
            val mb = dat.length() / (1024 * 1024)
            tvStatus.text = "${songEntries.size} songs ready | Data: ${mb}MB"
            btnAction.text = "Change .dat file"
            btnAction.setOnClickListener { showDatOptions() }
        } else {
            tvStatus.text = "${songEntries.size} songs loaded | Need songfile.dat"
            btnAction.text = "Get songfile.dat"
            btnAction.setOnClickListener { showDatOptions() }
        }
    }

    private fun showDatOptions() {
        val options = arrayOf("Download from URL (GitHub Release)", "Browse folder on device", "Cancel")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Get songfile.dat")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDownloadDialog()
                    1 -> pickFolder()
                }
            }
            .show()
    }

    private fun showDownloadDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            setText(AppSettings.downloadUrl.ifEmpty {
                "https://github.com/YOUR_USER/YOUR_REPO/releases/download/v1.0/songfile.dat"
            })
            selectAll()
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Download songfile.dat")
            .setMessage("Enter the download URL (e.g., GitHub Release asset link):")
            .setView(input)
            .setPositiveButton("Download") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    AppSettings.downloadUrl = url
                    downloadDat(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadDat(url: String) {
        tvStatus.text = "Downloading songfile.dat..."
        btnAction.isEnabled = false

        Thread {
            try {
                val destDir = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
                destDir.mkdirs()
                val destFile = File(destDir, "songfile.dat")

                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle("Denoy Karaoke")
                    .setDescription("Downloading songfile.dat (596MB)")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(destFile))
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                val manager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)

                requireActivity().runOnUiThread {
                    tvStatus.text = "Download started. Check notification bar."
                    btnAction.text = "Check download status"
                    btnAction.isEnabled = true
                    btnAction.setOnClickListener { showDatOptions() }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    tvStatus.text = "Download failed: ${e.message}"
                    btnAction.isEnabled = true
                }
            }
        }.start()
    }

    private fun copyDatFromFolder(folderUri: Uri) {
        tvStatus.text = "Copying songfile.dat..."
        Thread {
            try {
                val docTree = DocumentFile.fromTreeUri(requireContext(), folderUri)
                    ?: throw Exception("Cannot access folder")

                for (child in docTree.listFiles()) {
                    val name = child.name?.lowercase() ?: continue
                    if (name.endsWith(".dat")) {
                        val dest = File(requireContext().getExternalFilesDir(null), "songfile.dat")
                        requireContext().contentResolver.openInputStream(child.uri)?.use { input ->
                            FileOutputStream(dest).use { output -> input.copyTo(output) }
                        }
                        datFile = dest
                        AppSettings.savedDatPath = dest.absolutePath
                        break
                    }
                }

                requireActivity().runOnUiThread { updateStatus() }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    tvStatus.text = "Error: ${e.message}"
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
    var savedDatPath: String? = null
    var downloadUrl: String = ""
}
