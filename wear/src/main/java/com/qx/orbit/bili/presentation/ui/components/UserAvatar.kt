package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import com.qx.orbit.bili.R
import com.qx.orbit.bili.util.fixCoverUrl
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

@Composable
fun UserAvatar(
    modifier: Modifier = Modifier,
    avatarUrl: String,
    officialRole: Int,
    isVip: Boolean = false,
    isLive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val boxModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(modifier = boxModifier) {
        val imageModifier = Modifier
            .fillMaxSize()
            .then(
                if (isLive) Modifier.padding(2.dp).border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFB7299), Color(0xFFFF85A1))
                    ),
                    shape = CircleShape
                ).padding(2.dp) else Modifier
            )
            .clip(CircleShape)

        if (avatarUrl.isNotEmpty()) {
            val baseAvatarUrl = avatarUrl.fixCoverUrl()
            val fixedAvatarUrl = if (baseAvatarUrl.contains("@")) baseAvatarUrl else "${baseAvatarUrl}@150w_150h_1c.webp"
            
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(fixedAvatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = imageModifier
            )
        } else {
            Box(
                modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(0.6f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (officialRole == 1) {
            Image(
                painter = painterResource(id = R.drawable.ic_certification_official),
                contentDescription = "Official Verification",
                modifier = Modifier
                    .fillMaxSize(0.35f)
                    .align(Alignment.BottomEnd)
            )
        } else if (officialRole >= 2) {
            Image(
                painter = painterResource(id = R.drawable.ic_certification_enterprise),
                contentDescription = "Enterprise Verification",
                modifier = Modifier
                    .fillMaxSize(0.35f)
                    .align(Alignment.BottomEnd)
            )
        } else if (isVip) {
            Image(
                painter = painterResource(id = R.drawable.icon_vip_badge_normal),
                contentDescription = "VIP",
                modifier = Modifier
                    .fillMaxSize(0.35f)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}
