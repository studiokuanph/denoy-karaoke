package com.denoy.karaoke.data.model

data class SongEntry(
    val title: String,
    val songId: Int,
    val datOffset: Long,
    val compressedSize: Int,
    var artist: String = "",
    var code: Int = 0
) {
    val safeFileName: String
        get() = title.replace(Regex("[^a-zA-Z0-9 _\\-.]"), "_")
}
