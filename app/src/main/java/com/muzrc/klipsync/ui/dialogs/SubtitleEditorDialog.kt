package com.muzrc.klipsync.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muzrc.klipsync.model.SubtitleFontFamily
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.SubtitlePosition
import com.muzrc.klipsync.util.TimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleEditorDialog(
    initialItem: SubtitleItem?,
    defaultStartMs: Long,
    maxTimelineMs: Long,
    onDismiss: () -> Unit,
    onSave: (SubtitleItem) -> Unit
) {
    val safeMaxMs = maxTimelineMs.coerceAtLeast(3000L)
    var text by remember { mutableStateOf(initialItem?.text ?: "") }
    var startMs by remember {
        mutableLongStateOf(initialItem?.startMs ?: defaultStartMs.coerceIn(0L, safeMaxMs - 500L))
    }
    var endMs by remember {
        mutableLongStateOf(
            initialItem?.endMs ?: (defaultStartMs + 3000L).coerceIn(500L, safeMaxMs)
        )
    }
    var fontFamily by remember {
        mutableStateOf(initialItem?.fontFamily ?: SubtitleFontFamily.SANS_SERIF)
    }
    var fontSizeSp by remember { mutableFloatStateOf(initialItem?.fontSizeSp ?: 22f) }
    var selectedColorHex by remember { mutableLongStateOf(initialItem?.textColorHex ?: 0xFFFFFFFF) }
    var hasDarkBackground by remember { mutableStateOf(initialItem?.hasDarkBackground ?: true) }
    var position by remember { mutableStateOf(initialItem?.position ?: SubtitlePosition.BOTTOM) }

    val colorChoices = listOf(
        0xFFFFFFFF, // Putih
        0xFFFACC15, // Kuning Emas
        0xFF38BDF8, // Biru Langit
        0xFF4ADE80, // Hijau Pudina
        0xFFF472B6, // Merah Jambu
        0xFFFB923C  // Jingga
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialItem == null) "Tambah Sarikata Manual" else "Edit Sarikata")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Teks Sarikata / Lirik") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Text(
                    text = "Masa Mula: ${TimeFormatter.formatPreciseMs(startMs)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = startMs.toFloat(),
                    onValueChange = {
                        startMs = it.toLong()
                        if (endMs <= startMs) {
                            endMs = (startMs + 500L).coerceAtMost(safeMaxMs)
                        }
                    },
                    valueRange = 0f..safeMaxMs.toFloat()
                )

                Text(
                    text = "Masa Tamat: ${TimeFormatter.formatPreciseMs(endMs)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = endMs.toFloat(),
                    onValueChange = {
                        endMs = it.toLong().coerceAtLeast(startMs + 200L)
                    },
                    valueRange = 0f..safeMaxMs.toFloat()
                )

                Text(
                    text = "Jenis Fon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleFontFamily.entries.forEach { family ->
                        FilterChip(
                            selected = fontFamily == family,
                            onClick = { fontFamily = family },
                            label = { Text(family.label) }
                        )
                    }
                }

                Text(
                    text = "Saiz Fon: ${fontSizeSp.toInt()} sp",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = fontSizeSp,
                    onValueChange = { fontSizeSp = it },
                    valueRange = 14f..40f
                )

                Text(
                    text = "Warna Teks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorChoices.forEach { hex ->
                        val isSelected = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex.toInt()))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }

                Text(
                    text = "Kedudukan Teks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitlePosition.entries.forEach { pos ->
                        FilterChip(
                            selected = position == pos,
                            onClick = { position = pos },
                            label = { Text(pos.label) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasDarkBackground,
                        onCheckedChange = { hasDarkBackground = it }
                    )
                    Text("Kotak latar belakang gelap lutsinar")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    val safeStart = startMs.coerceAtLeast(0L)
                    val safeEnd = endMs.coerceAtLeast(safeStart + 200L)

                    val baseItem = initialItem ?: SubtitleItem(
                        text = text.trim(),
                        startMs = safeStart,
                        endMs = safeEnd
                    )

                    val saved = baseItem.copy(
                        text = text.trim(),
                        startMs = safeStart,
                        endMs = safeEnd,
                        fontFamily = fontFamily,
                        fontSizeSp = fontSizeSp.coerceIn(8f, 72f),
                        textColorHex = selectedColorHex,
                        hasDarkBackground = hasDarkBackground,
                        position = position
                    )
                    onSave(saved)
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
