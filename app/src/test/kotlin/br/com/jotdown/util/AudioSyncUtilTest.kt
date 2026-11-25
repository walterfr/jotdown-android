package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSyncUtilTest {

    @Test
    fun formatDurationMs_formatsMinutesSecondsHoursAndClampsNegative() {
        assertEquals("00:00", AudioSyncUtil.formatDurationMs(-500L))
        assertEquals("00:00", AudioSyncUtil.formatDurationMs(0L))
        assertEquals("01:05", AudioSyncUtil.formatDurationMs(65_000L))
        assertEquals("1:01:01", AudioSyncUtil.formatDurationMs(3_661_000L))
    }

    @Test
    fun shouldDimStrokeForPlayback_dimsOnlyFutureStrokesOfActiveRecording() {
        // Not playing -> never dimmed
        assertFalse(AudioSyncUtil.shouldDimStrokeForPlayback(1L, 5000L, null, 1000L))

        // Different recording -> never dimmed
        assertFalse(AudioSyncUtil.shouldDimStrokeForPlayback(2L, 5000L, 1L, 1000L))

        // Untouched/untagged stroke -> never dimmed
        assertFalse(AudioSyncUtil.shouldDimStrokeForPlayback(null, 5000L, 1L, 1000L))

        // Same recording, timestamp <= position -> revealed (not dimmed)
        assertFalse(AudioSyncUtil.shouldDimStrokeForPlayback(1L, 1000L, 1L, 1000L))
        assertFalse(AudioSyncUtil.shouldDimStrokeForPlayback(1L, 900L, 1L, 1000L))

        // Same recording, timestamp > position -> dimmed
        assertTrue(AudioSyncUtil.shouldDimStrokeForPlayback(1L, 1001L, 1L, 1000L))
    }

    @Test
    fun isTapOnAudioStroke_hitsNearSegmentAndMissesFarPoint() {
        val pts = listOf(GeomPoint(10f, 10f), GeomPoint(110f, 10f))
        assertTrue(AudioSyncUtil.isTapOnAudioStroke(60f, 18f, pts, hitRadius = 12f))
        assertFalse(AudioSyncUtil.isTapOnAudioStroke(60f, 40f, pts, hitRadius = 12f))
        assertFalse(AudioSyncUtil.isTapOnAudioStroke(60f, 10f, emptyList(), hitRadius = 12f))
    }
}
