package com.denoy.karaoke.data.model

data class SongDatabase(
    val entries: List<SongEntry>,
    val header: List<Long>,
    val totalSongs: Int
)
