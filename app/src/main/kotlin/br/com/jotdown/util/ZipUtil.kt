package br.com.jotdown.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {

    fun zipDirectory(directoryToZip: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zipFile(directoryToZip, directoryToZip.name, zos)
        }
    }

    private fun zipFile(fileToZip: File, fileName: String, zos: ZipOutputStream) {
        if (fileToZip.isHidden) {
            return
        }
        if (fileToZip.isDirectory) {
            val children = fileToZip.listFiles()
            for (childFile in children ?: emptyArray()) {
                zipFile(childFile, fileName + "/" + childFile.name, zos)
            }
            return
        }
        val fis = FileInputStream(fileToZip)
        val zipEntry = ZipEntry(fileName)
        zos.putNextEntry(zipEntry)
        fis.copyTo(zos)
        fis.close()
    }

    fun unzip(zipFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = File(destDir, zipEntry.name)
                // Strip the top-level directory name that was added during zip
                // Actually, let's keep it simple: we unzip and then we'll have to know the exact path
                // Or better, don't include the top level dir name in zipFile() so it unzips neatly.
                // Since this is a utility, let's just write exactly as in the zip.
                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    File(newFile.parent!!).mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                zipEntry = zis.nextEntry
            }
        }
    }
}
