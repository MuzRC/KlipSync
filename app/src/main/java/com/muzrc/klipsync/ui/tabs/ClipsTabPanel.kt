package com.muzrc.klipsync.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.West
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        selectedLabelColor = MaterialTheme.colorScheme.primary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Baris Butang Utama (Bersih tanpa butang terapung bertindih)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoCall,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "+ Video",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "+ Gambar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        if (clips.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Belum Ada Klip Visual",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tekan butang + Video atau + Gambar di atas untuk mula menyusun babak video anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (clips.isNotEmpty() && hasLongerAudio) {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Apabila Visual Tamat Awal Daripada Lagu",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VideoEndBehavior.entries.forEach { behavior ->
                            val isSelected = transitionConfig.endBehavior == behavior
                            FilterChip(
                                selected = isSelected,
                                onClick = { onEndBehaviorChange(behavior) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = iconForEndBehavior(behavior),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(behavior.label, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }
        }

        if (showTransitionControls) {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        JoinMode.entries.forEach { mode ->
                            val isSelected = transitionConfig.joinMode == mode
                            val joinIcon = if (mode == JoinMode.OVERLAP) {
                                Icons.Rounded.Layers
                            } else {
                                Icons.Rounded.LinearScale
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { onJoinModeChange(mode) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = joinIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(mode.label, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    Text(
                        text = "Transisi Antara Babak & Loop",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TransitionType.entries.forEach { type ->
                            val isSelected = transitionConfig.transitionType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTransitionTypeChange(type) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = iconForTransition(type),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(type.label, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    if (transitionConfig.joinMode == JoinMode.OVERLAP ||
                        transitionConfig.transitionType != TransitionType.NONE
                    ) {
                        val sec = transitionConfig.durationMs / 1000f
                        Text(
                            text = "Tempoh Transisi / Pertindihan: ${String.format(Locale.US, "%.1f", sec)}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = transitionConfig.durationMs.toFloat(),
                            onValueChange = { onTransitionDurationChange(it.toLong()) },
                            valueRange = 300f..2500f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
            }
        }

        clips.forEachIndexed { index, clip ->
            val isSelected = clip.id == selectedClipId
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { onSelectClip(clip.id) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onMoveUp(index) },
                                enabled = index > 0,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    contentDescription = "Naik",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveDown(index) },
                                enabled = index < clips.lastIndex,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowDownward,
                                    contentDescription = "Turun",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRemoveClip(clip.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Padam",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
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
                            valueRange = 1000f..30000f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.height(30.dp)
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
                                valueRange = 0f..clip.originalDurationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ControlCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Gerakan Kamera (Zoom / Pan)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CameraMotionEffect.entries.forEach { motion ->
                            val motionSelected = clip.motionEffect == motion
                            FilterChip(
                                selected = motionSelected,
                                onClick = { onMotionEffectChange(clip.id, motion) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = motionSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = iconForCameraMotion(motion),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(motion.label, fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
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
                            valueRange = 0.05f..0.50f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCutClip(clip.id) },
                            enabled = clip.singlePlayDurationMs >= 500L,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Belah", fontSize = 11.5.sp, modifier = Modifier.padding(start = 4.dp))
                        }

                        OutlinedButton(
                            onClick = { onRotateClip(clip.id) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("${clip.rotationDegrees}°", fontSize = 11.5.sp, modifier = Modifier.padding(start = 4.dp))
                        }

                        OutlinedButton(
                            onClick = { onMirrorClip(clip.id) },
                            border = BorderStroke(
                                1.dp,
                                if (clip.isMirrored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(if (clip.isMirrored) "Cermin: Ya" else "Cermin", fontSize = 11.5.sp, modifier = Modifier.padding(start = 4.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onLoopCountChange(clip.id, clip.loopCount - 1) },
                                enabled = clip.loopCount > 1,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("-")
                            }
                            Text(
                                text = "Loop ${clip.loopCount}x",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            OutlinedButton(
                                onClick = { onLoopCountChange(clip.id, clip.loopCount + 1) },
                                enabled = clip.loopCount < 20,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

private fun iconForCameraMotion(effect: CameraMotionEffect): ImageVector {
    return when (effect) {
        CameraMotionEffect.NONE -> Icons.Rounded.Block
        CameraMotionEffect.ZOOM_IN -> Icons.Rounded.ZoomIn
        CameraMotionEffect.ZOOM_OUT -> Icons.Rounded.ZoomOut
        CameraMotionEffect.PAN_LEFT -> Icons.Rounded.West
        CameraMotionEffect.PAN_RIGHT -> Icons.Rounded.East
    }
}

private fun iconForEndBehavior(behavior: VideoEndBehavior): ImageVector {
    return when {
        behavior == VideoEndBehavior.LOOP_TO_AUDIO -> Icons.Rounded.Repeat
        behavior.label.contains("Zoom", ignoreCase = true) -> Icons.Rounded.CenterFocusStrong
        else -> Icons.Rounded.StopCircle
    }
}

private fun iconForTransition(type: TransitionType): ImageVector {
    return when {
        type == TransitionType.NONE -> Icons.Rounded.Block
        type.label.contains("Cross", ignoreCase = true) -> Icons.Rounded.BlurOn
        type.label.contains("Fade", ignoreCase = true) -> Icons.Rounded.Contrast
        type.label.contains("Slide", ignoreCase = true) -> Icons.Rounded.Swipe
        type.label.contains("Zoom", ignoreCase = true) -> Icons.Rounded.ZoomIn
        else -> Icons.Rounded.AutoAwesomeMotion
    }
}
