package br.com.jotdown.data.sync

data class DriveFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: String = ""
)
