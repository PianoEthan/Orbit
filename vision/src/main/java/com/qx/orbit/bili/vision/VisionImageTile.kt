package com.qx.orbit.bili.vision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun VisionImageTile(url: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(onClick = onClick, enabled = url.isNotBlank(), interactionSource = interaction,
        modifier = modifier.semantics { contentDescription = label }, shape = if (focused) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {
        AsyncImage(url, label, contentScale = ContentScale.Fit)
    }
}
