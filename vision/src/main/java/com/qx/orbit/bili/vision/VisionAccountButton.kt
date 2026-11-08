package com.qx.orbit.bili.vision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun VisionAccountButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val profile = VisionUserSession.profile
    val label = if (VisionUserSession.isLoggedIn) {
        "打开个人中心，${profile?.uname.orEmpty().ifBlank { "已登录" }}"
    } else "打开个人中心，未登录"
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (profile?.isLogin == true && profile.face.isNotBlank()) {
                AsyncImage(profile.face, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape))
            } else {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
    }
}
