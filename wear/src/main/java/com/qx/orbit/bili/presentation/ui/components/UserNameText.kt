package com.qx.orbit.bili.presentation.ui.components

import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.qx.orbit.bili.presentation.theme.BiliPink

@Composable
fun UserNameText(
    name: String,
    isVip: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Text(
        text = name,
        style = style,
        color = if (isVip) BiliPink else color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow
    )
}
