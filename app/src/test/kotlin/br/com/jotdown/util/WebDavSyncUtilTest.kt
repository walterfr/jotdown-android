package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSyncUtilTest {

    @Test
    fun normalizeRemoteUrl_enforcesHttpsAndJoinsCleanly() {
        assertEquals(
            "https://cloud.example.com/dav/jotdown_backup.zip",
            WebDavSyncUtil.normalizeRemoteUrl("https://cloud.example.com/dav/", "/jotdown_backup.zip")
        )
        assertNull(WebDavSyncUtil.normalizeRemoteUrl("http://192.168.1.50:8080/backups", "lib.zip"))
        assertNull(WebDavSyncUtil.normalizeRemoteUrl("ftp://example.com/dav", "backup.zip"))
        assertNull(WebDavSyncUtil.normalizeRemoteUrl("https://", "backup.zip"))
        assertNull(WebDavSyncUtil.normalizeRemoteUrl("https://example.com", "   "))
    }

    @Test
    fun parentCollectionUrl_extractsHttpsCollectionUrlAndRejectsHttp() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/user/Jotdown/",
            WebDavSyncUtil.parentCollectionUrl("https://cloud.example.com/remote.php/dav/files/user/Jotdown/backup.zip")
        )
        assertNull(WebDavSyncUtil.parentCollectionUrl("http://cloud.example.com/dav/backup.zip"))
        assertNull(WebDavSyncUtil.parentCollectionUrl("https://cloud.example.com"))
        assertNull(WebDavSyncUtil.parentCollectionUrl("invalid-url"))
    }

    @Test
    fun isPlainHttp_detectsHttpOnly() {
        assertTrue(WebDavSyncUtil.isPlainHttp("http://nas.local/webdav"))
        assertTrue(WebDavSyncUtil.isPlainHttp("  HTTP://nas.local/webdav"))
        assertFalse(WebDavSyncUtil.isPlainHttp("https://nas.local/webdav"))
    }

    @Test
    fun buildBasicAuthHeader_encodesUtf8Credentials() {
        assertEquals(
            "Basic dXNlcjpwYXNz",
            WebDavSyncUtil.buildBasicAuthHeader("user", "pass")
        )
    }

    @Test
    fun isSuccessfulStatus_accepts2xxAnd207() {
        assertTrue(WebDavSyncUtil.isSuccessfulStatus(200))
        assertTrue(WebDavSyncUtil.isSuccessfulStatus(201))
        assertTrue(WebDavSyncUtil.isSuccessfulStatus(204))
        assertTrue(WebDavSyncUtil.isSuccessfulStatus(207))
        assertFalse(WebDavSyncUtil.isSuccessfulStatus(401))
        assertFalse(WebDavSyncUtil.isSuccessfulStatus(404))
        assertFalse(WebDavSyncUtil.isSuccessfulStatus(500))
    }

    @Test
    fun isValidZipHeader_checksPkMagicBytes() {
        assertTrue(WebDavSyncUtil.isValidZipHeader(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)))
        assertFalse(WebDavSyncUtil.isValidZipHeader(byteArrayOf(0x50, 0x4B, 0x03)))
        assertFalse(WebDavSyncUtil.isValidZipHeader("<html/>".toByteArray()))
    }
}
