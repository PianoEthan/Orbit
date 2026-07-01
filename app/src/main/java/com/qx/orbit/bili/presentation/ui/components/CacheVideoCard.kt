package com.qx.orbit.bili.presentation.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qx.orbit.bili.utils.VideoDownloadManager
import java.io.File

@SuppressLint("DefaultLocale")
@Composable
fun CacheVideoCard(
    modifier: Modifier = Modifier,
    item: VideoDownloadManager.DownloadInfo,
    onClick: () -> Unit,
    statusText: String? = null,
    transformation: SurfaceTransformation? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(110.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        transformation = transformation
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val coverFile = if (item.localUri != null) {
                File(item.localUri).parentFile?.let { File(it, "${item.filename}.cover.webp") }
            } else null
            
            val model = if (coverFile?.exists() == true) {
                coverFile
            } else if (item.coverUrl.isNotEmpty()) {
                if (item.coverUrl.contains("@")) item.coverUrl else "${item.coverUrl}@480w_270h_1c.webp"
            } else null
            
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Dark gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
            
            // Text Content (Top to Bottom)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall.copy(
                            shadow = Shadow(
                                color = Color.Black,
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        ),
                        color = Color.White
                    )
                }
                
                // Bottom Row: UP Name and View Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val fileSizeText = if (item.totalBytes > 0) {
                        val kb = item.totalBytes / 1024.0
                        val mb = kb / 1024.0
                        val gb = mb / 1024.0
                        when {
                            gb >= 1.0 -> String.format("%.2f GB", gb)
                            mb >= 1.0 -> String.format("%.2f MB", mb)
                            else -> String.format("%.2f KB", kb)
                        }
                    } else "未知大小"

                    Text(
                        text = statusText ?: fileSizeText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = Shadow(
                                color = Color.Black,
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        ),
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    val formattedDuration = if (item.duration > 0) {
                        String.format("%02d:%02d", item.duration / 60, item.duration % 60)
                    } else "--:--"

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (statusText == null) {
                            Text(
                                text = formattedDuration,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(2f, 2f),
                                        blurRadius = 4f
                                    )
                                ),
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
