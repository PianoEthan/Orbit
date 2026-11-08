package com.qx.orbit.bili.vision

import androidx.compose.foundation.focusGroup
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun VisionUserTabs(activeTab: UserCenterTab, onSelect: (UserCenterTab) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = activeTab.ordinal, modifier = Modifier.focusGroup(), edgePadding = 16.dp, divider = {}) {
        UserCenterTab.entries.forEach { tab ->
            Tab(selected = activeTab == tab, onClick = { onSelect(tab) }, text = { Text(tab.title) })
        }
    }
}
