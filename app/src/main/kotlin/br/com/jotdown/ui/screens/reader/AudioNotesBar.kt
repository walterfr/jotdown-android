package br.com.jotdown.ui.screens.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.R
import br.com.jotdown.data.entity.AudioRecordingEntity
import br.com.jotdown.util.AudioSyncUtil

@Composable
fun AudioNotesBar(
    recordings: List<AudioRecordingEntity>,
    selectedRecording: AudioRecordingEntity?,
    isRecording: Boolean,
    recordingElapsedMs: Long,
    playingRecordingId: Long?,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onSelectRecording: (AudioRecordingEntity) -> Unit,
    onToggleRecord: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDeleteRecording: (AudioRecordingEntity) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val activeDurationMs = remember(selectedRecording, playingRecordingId, playbackDurationMs) {
        if (selectedRecording != null && playingRecordingId == selectedRecording.id && playbackDurationMs > 0L) {
            playbackDurationMs
        } else {
            selectedRecording?.durationMs ?: 0L
        }
    }
    val activePosMs = if (selectedRecording != null && playingRecordingId == selectedRecording.id) {
        playbackPositionMs.coerceIn(0L, activeDurationMs.coerceAtLeast(1L))
    } else {
        0L
    }

    val recordBtnColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.primaryContainer,
        label = "recColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(recordBtnColor)
                        .clickable { onToggleRecord() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = stringResource(
                            if (isRecording) R.string.audio_record_stop else R.string.audio_record_start
                        ),
                        tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isRecording) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                        )
                        Text(
                            text = AudioSyncUtil.formatDurationMs(recordingElapsedMs),
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    if (selectedRecording != null) {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying && playingRecordingId == selectedRecording.id) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = stringResource(
                                    if (isPlaying && playingRecordingId == selectedRecording.id) {
                                        R.string.audio_pause
                                    } else {
                                        R.string.audio_play
                                    }
                                ),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (activeDurationMs > 0L) {
                            Slider(
                                value = activePosMs.toFloat(),
                                onValueChange = { onSeekTo(it.toLong()) },
                                valueRange = 0f..activeDurationMs.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${AudioSyncUtil.formatDurationMs(activePosMs)} / ${AudioSyncUtil.formatDurationMs(activeDurationMs)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = selectedRecording.title.ifBlank {
                                    stringResource(R.string.audio_recording_title_prefix, 1)
                                },
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.readerbar_audio_notes),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!isRecording && recordings.size > 1) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showPicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedRecording?.title?.ifBlank {
                                        stringResource(R.string.audio_recording_title_prefix, 1)
                                    } ?: "",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                            recordings.forEachIndexed { idx, rec ->
                                val label = rec.title.ifBlank {
                                    stringResource(R.string.audio_recording_title_prefix, recordings.size - idx)
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text("$label (${AudioSyncUtil.formatDurationMs(rec.durationMs)})")
                                    },
                                    onClick = {
                                        showPicker = false
                                        onSelectRecording(rec)
                                    }
                                )
                            }
                        }
                    }
                }

                if (!isRecording && selectedRecording != null) {
                    IconButton(
                        onClick = { onDeleteRecording(selectedRecording) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.audio_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!isRecording && !isPlaying) {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.audio_close),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
