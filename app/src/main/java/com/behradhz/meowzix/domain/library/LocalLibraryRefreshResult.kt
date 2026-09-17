package com.behradhz.meowzix.domain.library

data class LocalLibraryRefreshResult(
    val scanned: Int,
    val added: Int,
    val updated: Int,
    val removed: Int,
)
