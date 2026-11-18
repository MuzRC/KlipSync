package com.muzrc.klipsync.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.muzrc.klipsync.model.EditorTab

@Composable
fun EditorTabBar(
    activeTab: EditorTab,
    onSelectTab: (EditorTab) -> Unit
) {
    val tabs = EditorTab.entries
    TabRow(
        selectedTabIndex = tabs.indexOf(activeTab),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEach { tab ->
            val selected = tab == activeTab
            Tab(
                selected = selected,
                onClick = { onSelectTab(tab) },
                text = {
                    Text(
                        text = tab.title,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}
