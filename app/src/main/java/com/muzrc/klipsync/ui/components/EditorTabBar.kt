package com.muzrc.klipsync.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzrc.klipsync.model.EditorTab

@Composable
fun EditorTabBar(
    activeTab: EditorTab,
    onSelectTab: (EditorTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(EditorTab.entries) { tab ->
                    val selected = tab == activeTab
                    val bgColor = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    }
                    val borderColor = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                    } else {
                        Color.Transparent
                    }
                    val contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(10.dp))
                            .clickable { onSelectTab(tab) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = iconForTab(tab),
                            contentDescription = tab.title,
                            tint = contentColor,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

private fun iconForTab(tab: EditorTab): ImageVector {
    return when (tab) {
        EditorTab.CLIPS -> Icons.Rounded.Movie
        EditorTab.SPEED -> Icons.Rounded.SlowMotionVideo
        EditorTab.COLOR -> Icons.Rounded.Tune
        EditorTab.AUDIO -> Icons.Rounded.Headphones
        EditorTab.BEAT -> Icons.Rounded.GraphicEq
        EditorTab.CROP -> Icons.Rounded.CropFree
        EditorTab.SUBTITLES -> Icons.Rounded.TextFields
        EditorTab.WATERMARK -> Icons.Rounded.PictureInPictureAlt
        EditorTab.DEV_LOG -> Icons.Rounded.Terminal
    }
}
