package br.com.jotdown.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages MediaRecorder and MediaPlayer lifecycles with deterministic cleanup and 100ms progress ticks.
 */
class AudioNoteManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingTickerJob: Job? = null
    private var playbackTickerJob: Job? = null
    private var recordingStartRealtimeMs: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingId = MutableStateFlow<Long?>(null)
    val recordingId: StateFlow<Long?> = _recordingId.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private val _playingRecordingId = MutableStateFlow<Long?>(null)
    val playingRecordingId: StateFlow<Long?> = _playingRecordingId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0L)
    val playbackDurationMs: StateFlow<Long> = _playbackDurationMs.asStateFlow()

    fun startRecording(
        context: Context,
        recordingId: Long,
        outFile: File
    ): Boolean {
        stopPlayback()
        if (_isRecording.value) {
            stopRecording()
        }

        outFile.parentFile?.mkdirs()
        val createdRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            createdRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = createdRecorder
            recordingStartRealtimeMs = SystemClock.elapsedRealtime()
            _isRecording.value = true
            _recordingId.value = recordingId
            _recordingElapsedMs.value = 0L

            recordingTickerJob?.cancel()
            recordingTickerJob = scope.launch {
                while (isActive && _isRecording.value) {
                    _recordingElapsedMs.value = (SystemClock.elapsedRealtime() - recordingStartRealtimeMs)
                        .coerceAtLeast(0L)
                    delay(100L)
                }
            }
            true
        } catch (_: Exception) {
            runCatching { createdRecorder.reset() }
            runCatching { createdRecorder.release() }
            recorder = null
            _isRecording.value = false
            _recordingId.value = null
            _recordingElapsedMs.value = 0L
            false
        }
    }

    fun stopRecording(): Long {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        val elapsed = if (_isRecording.value) {
            (SystemClock.elapsedRealtime() - recordingStartRealtimeMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val activeRecorder = recorder
        try {
            activeRecorder?.stop()
        } catch (_: RuntimeException) {
            // Immediate stop (<500ms) or illegal state can throw RuntimeException; release in finally.
        } finally {
            runCatching { activeRecorder?.reset() }
            runCatching { activeRecorder?.release() }
            recorder = null
            _isRecording.value = false
            _recordingId.value = null
            _recordingElapsedMs.value = 0L
        }
        return elapsed
    }

    fun playRecording(recordingId: Long, filePath: File, startMs: Long = 0L): Boolean {
        if (!filePath.exists()) return false
        if (_isRecording.value) {
            stopRecording()
        }
        stopPlayback()

        val createdPlayer = MediaPlayer()
        return try {
            createdPlayer.setDataSource(filePath.absolutePath)
            createdPlayer.prepare()
            val durationMs = createdPlayer.duration.toLong().coerceAtLeast(0L)
            val clampedStartMs = startMs.coerceIn(0L, durationMs)
            if (clampedStartMs > 0L) {
                createdPlayer.seekTo(clampedStartMs.toInt())
            }
            createdPlayer.setOnCompletionListener {
                stopPlayback()
            }
            createdPlayer.start()
            player = createdPlayer
            _playingRecordingId.value = recordingId
            _isPlaying.value = true
            _playbackDurationMs.value = durationMs
            _playbackPositionMs.value = clampedStartMs
            startPlaybackTicker()
            true
        } catch (_: Exception) {
            runCatching { createdPlayer.release() }
            player = null
            _playingRecordingId.value = null
            _isPlaying.value = false
            _playbackPositionMs.value = 0L
            _playbackDurationMs.value = 0L
            false
        }
    }

    fun togglePlayPause() {
        val activePlayer = player ?: return
        try {
            if (activePlayer.isPlaying) {
                activePlayer.pause()
                _isPlaying.value = false
                playbackTickerJob?.cancel()
                playbackTickerJob = null
            } else {
                activePlayer.start()
                _isPlaying.value = true
                startPlaybackTicker()
            }
        } catch (_: IllegalStateException) {
            stopPlayback()
        }
    }

    fun seekTo(ms: Long) {
        val activePlayer = player ?: return
        val clamped = ms.coerceIn(0L, _playbackDurationMs.value.coerceAtLeast(0L))
        try {
            activePlayer.seekTo(clamped.toInt())
            _playbackPositionMs.value = clamped
        } catch (_: IllegalStateException) {
            stopPlayback()
        }
    }

    fun stopPlayback() {
        playbackTickerJob?.cancel()
        playbackTickerJob = null
        val activePlayer = player
        try {
            if (activePlayer?.isPlaying == true) {
                activePlayer.stop()
            }
        } catch (_: IllegalStateException) {
        } finally {
            runCatching { activePlayer?.reset() }
            runCatching { activePlayer?.release() }
            player = null
            _playingRecordingId.value = null
            _isPlaying.value = false
            _playbackPositionMs.value = 0L
            _playbackDurationMs.value = 0L
        }
    }

    fun releaseAll() {
        if (_isRecording.value) {
            stopRecording()
        }
        stopPlayback()
        scope.cancel()
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val activePlayer = player ?: break
                try {
                    _playbackPositionMs.value = activePlayer.currentPosition.toLong().coerceAtLeast(0L)
                } catch (_: IllegalStateException) {
                    break
                }
                delay(100L)
            }
        }
    }
}
