package com.muzrc.klipsync.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzrc.klipsync.model.CameraMotionEffect
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.TransitionConfig
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.model.VideoEndBehavior
import com.muzrc.klipsync.util.TimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipsTabPanel(
    clips: List<VideoClip>,
    selectedClipId: String?,
    transitionConfig: TransitionConfig,
    hasLongerAudio: Boolean,
    onSelectClip: (String) -> Unit,
    onAddClips: (List<Uri>) -> Unit,
    onRemoveClip: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit,
    onImageDurationChange: (String, Long) -> Unit,
    onCutClip: (String) -> Unit,
    onLoopCountChange: (String, Int) -> Unit,
    onRotateClip: (String) -> Unit,
    onMirrorClip: (String) -> Unit,
    onMotionEffectChange: (String, CameraMotionEffect) -> Unit,
    onMotionIntensityChange: (String, Float) -> Unit,
    onApplyMotionToAll: (CameraMotionEffect, Float) -> Unit,
    onJoinModeChange: (JoinMode) -> Unit,
    onTransitionTypeChange: (TransitionType) -> Unit,
    onTransitionDurationChange: (Long) -> Unit,
    onEndBehaviorChange: (VideoEndBehavior) -> Unit
) {
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddClips(uris)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddClips(uris)
        }
    }

    val isLoopingActive = clips.any { it.loopCount > 1 } ||
        (clips.isNotEmpty() && hasLongerAudio && transitionConfig.endBehavior == VideoEndBehavior.LOOP_TO_AUDIO)
    val showTransitionControls = clips.size > 1 || isLoopingActive

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.VideoCall, contentDescription = null)
                Text(
                    text = "+ Video",
                    modifier = Modifier.padding(start = 6.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                Text(
                    text = "+ Gambar",
                    modifier = Modifier.padding(start = 6.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (clips.isNotEmpty() && hasLongerAudio) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Apabila Visual Tamat Awal Daripada Lagu",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoEndBehavior.entries.forEach { behavior ->
                            FilterChip(
                                selected = transitionConfig.endBehavior == behavior,
                                onClick = { onEndBehaviorChange(behavior) },
                                label = { Text(behavior.label) }
                            )
                        }
                    }
                }
            }
        }

        if (showTransitionControls) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Cara Gabungan Klip / Gambar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JoinMode.entries.forEach { mode ->
                            FilterChip(
                                selected = transitionConfig.joinMode == mode,
                                onClick = { onJoinModeChange(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Text(
                        text = "Transisi Antara Babak & Loop",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransitionType.entries.forEach { type ->
                            FilterChip(
                                selected = transitionConfig.transitionType == type,
                                onClick = { onTransitionTypeChange(type) },
                                label = { Text(type.label) }
                            )
                        }
                    }

                    if (transitionConfig.joinMode == JoinMode.OVERLAP ||
                        transitionConfig.transitionType != TransitionType.NONE
                    ) {
                        val sec = transitionConfig.durationMs / 1000f
                        Text(
                            text = "Tempoh Transisi / Pertindihan: ${String.format(Locale.US, "%.1f", sec)}s",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = transitionConfig.durationMs.toFloat(),
                            onValueChange = { onTransitionDurationChange(it.toLong()) },
                            valueRange = 300f..2500f
                        )
                    }
                }
            }
        }

        clips.forEachIndexed { index, clip ->
            val isSelected = clip.id == selectedClipId
            Card(
                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { onSelectClip(clip.id) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val typePrefix = if (clip.isImage) "[Gambar] " else ""
                        Text(
                            text = "${index + 1}. $typePrefix${clip.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row {
                            IconButton(
                                onClick = { onMoveUp(index) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naik")
                            }
                            IconButton(
                                onClick = { onMoveDown(index) },
                                enabled = index < clips.lastIndex
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turun")
                            }
                            IconButton(onClick = { onRemoveClip(clip.id) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Padam",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (clip.isImage) {
                        val sec = clip.singlePlayDurationMs / 1000f
                        Text(
                            text = "Tempoh Paparan Gambar: ${String.format(Locale.US, "%.1f", sec)} saat",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = clip.singlePlayDurationMs.coerceIn(1000L, 30000L).toFloat(),
                            onValueChange = { onImageDurationChange(clip.id, it.toLong()) },
                            valueRange = 1000f..30000f
                        )
                    } else {
                        Text(
                            text = "Potong: ${TimeFormatter.formatPreciseMs(clip.trimStartMs)} – ${TimeFormatter.formatPreciseMs(clip.trimEndMs)} • Laju ${clip.speed}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (clip.originalDurationMs > 300L) {
                            RangeSlider(
                                value = clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat(),
                                onValueChange = { range ->
                                    onTrimClip(
                                        clip.id,
                                        range.start.toLong(),
                                        range.endInclusive.toLong()
                                    )
                                },
                                valueRange = 0f..clip.originalDurationMs.toFloat()
                            )
                        }
                    }

                    // Pilihan Gerakan Kamera (Zoom In / Zoom Out / Pan)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Gerakan Kamera (Zoom / Pan)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (clips.size > 1) {
                            TextButton(
                                onClick = { onApplyMotionToAll(clip.motionEffect, clip.motionIntensity) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Guna Semua", fontSize = 11.sp)
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CameraMotionEffect.entries.forEach { motion ->
                            FilterChip(
                                selected = clip.motionEffect == motion,
                                onClick = { onMotionEffectChange(clip.id, motion) },
                                label = { Text(motion.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (clip.motionEffect != CameraMotionEffect.NONE) {
                        val pct = (clip.motionIntensity * 100).toInt()
                        val amountLabel = when (clip.motionEffect) {
                            CameraMotionEffect.ZOOM_IN -> "Tahap Zoom In: +$pct% (1.00x → ${String.format(Locale.US, "%.2fx", 1.0f + clip.motionIntensity)})"
                            CameraMotionEffect.ZOOM_OUT -> "Tahap Zoom Out: -$pct% (${String.format(Locale.US, "%.2fx", 1.0f + clip.motionIntensity)} → 1.00x)"
                            CameraMotionEffect.PAN_LEFT,
                            CameraMotionEffect.PAN_RIGHT -> "Jarak Gerakan Pan: $pct%"
                            else -> ""
                        }
                        Text(
                            text = amountLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = clip.motionIntensity,
                            onValueChange = { onMotionIntensityChange(clip.id, it) },
                            valueRange = 0.05f..0.50f
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = { onCutClip(clip.id) },
                            enabled = clip.singlePlayDurationMs >= 500L,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("Belah", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }

                        OutlinedButton(
                            onClick = { onRotateClip(clip.id) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("${clip.rotationDegrees}°", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }

                        OutlinedButton(
                            onClick = { onMirrorClip(clip.id) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text(if (clip.isMirrored) "Cermin: Ya" else "Cermin", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onLoopCountChange(clip.id, clip.loopCount - 1) },
                                enabled = clip.loopCount > 1,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("-")
                            }
                            Text(
                                text = "Loop ${clip.loopCount}x",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            OutlinedButton(
                                onClick = { onLoopCountChange(clip.id, clip.loopCount + 1) },
                                enabled = clip.loopCount < 20,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("+")
                            }
                        }
                    }
                }
            }
        }
    }
}
