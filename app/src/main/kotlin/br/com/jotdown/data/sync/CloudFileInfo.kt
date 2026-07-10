package br.com.jotdown.data.sync

data class CloudFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: String = "",
    val isFolder: Boolean = false,
    val mimeType: String = ""
)
