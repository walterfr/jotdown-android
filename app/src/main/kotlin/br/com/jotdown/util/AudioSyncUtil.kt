package br.com.jotdown.util

import java.util.Locale

/**
 * Pure Kotlin helpers for synced audio note formatting, playback dimming, and stroke tap hit-testing.
 */
object AudioSyncUtil {

    fun formatDurationMs(ms: Long): String {
        val clampedMs = ms.coerceAtLeast(0L)
        val totalSeconds = clampedMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun shouldDimStrokeForPlayback(
        strokeAudioId: Long?,
        strokeTimestampMs: Long,
        playingAudioId: Long?,
        playbackPositionMs: Long
    ): Boolean {
        return playingAudioId != null &&
            strokeAudioId == playingAudioId &&
            strokeTimestampMs > playbackPositionMs
    }

    fun isTapOnAudioStroke(
        tapX: Float,
        tapY: Float,
        strokePoints: List<GeomPoint>,
        hitRadius: Float
    ): Boolean {
        if (strokePoints.isEmpty()) return false
        return StrokeGeometryUtil.doesEraserHitPath(
            eraserPoints = listOf(GeomPoint(tapX, tapY)),
            pathPoints = strokePoints,
            hitRadius = hitRadius.coerceAtLeast(1f)
        )
    }
}
