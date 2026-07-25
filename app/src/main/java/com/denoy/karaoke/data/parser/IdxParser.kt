package com.denoy.karaoke.data.parser

import com.denoy.karaoke.data.model.SongDatabase
import com.denoy.karaoke.data.model.SongEntry
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class IdxParser(private val path: String) {

    fun parse(): SongDatabase {
        val file = RandomAccessFile(path, "r")
        val data = ByteArray(file.length().toInt())
        file.readFully(data)
        file.close()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Parse header: 28 uint32 values
        val header = mutableListOf<Long>()
        for (i in 0 until 28) {
            header.add(buffer.getInt().toLong() and 0xFFFFFFFFL)
        }

        val entries = mutableListOf<SongEntry>()

        // Find song table - look for "#9 Dream" marker
        val songStart = findSongTableStart(data)
        if (songStart < 0) return SongDatabase(entries, header, 0)

        var pos = songStart
        while (pos < data.size - 12) {
            // Find 0x01 separator
            val sep = data.indexOf(0x01.toByte(), pos)
            if (sep < 0 || sep - pos > 200 || sep - pos < 1) {
                pos = sep + 1
                continue
            }

            val titleLen = sep - pos
            val titleBytes = data.copyOfRange(pos, sep)
            val title = String(titleBytes, StandardCharsets.US_ASCII)
                .replace(Regex("[^\\x20-\\x7E]"), "")
                .trim()

            if (title.isEmpty() || !title[0].isPrintable()) {
                pos = sep + 1
                continue
            }

            if (sep + 12 > data.size) break

            val songId = ByteBuffer.wrap(data, sep + 1, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt() and 0xFFFFFF
            if (songId > 100000) break

            val offset = ByteBuffer.wrap(data, sep + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt().toLong() and 0xFFFFFFFFL
            val compSize = ByteBuffer.wrap(data, sep + 8, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt()
            if (offset > 600_000_000 || compSize > 200_000_000) break

            entries.add(SongEntry(
                title = title,
                songId = songId,
                datOffset = offset,
                compressedSize = compSize
            ))

            pos = sep + 12
        }

        return SongDatabase(entries, header, entries.size)
    }

    private fun findSongTableStart(data: ByteArray): Int {
        val marker = "\u0001#9 Dream".toByteArray(StandardCharsets.US_ASCII)
        var idx = data.indexOf(marker)
        if (idx >= 0) return idx + 1
        val marker2 = "#9 Dream".toByteArray(StandardCharsets.US_ASCII)
        idx = data.indexOf(marker2)
        return idx
    }

    private fun Char.isPrintable(): Boolean = this in ' '..'~'

    private fun ByteArray.indexOf(target: Byte, startIndex: Int): Int {
        for (i in startIndex until size) {
            if (this[i] == target) return i
        }
        return -1
    }

    private fun ByteArray.indexOf(target: ByteArray): Int {
        outer@ for (i in 0..size - target.size) {
            for (j in target.indices) {
                if (this[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
