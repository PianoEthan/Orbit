package com.qx.orbit.bili.presentation.ui.components

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.Check
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior

data class MediaItem(
    val uri: Uri,
    val path: String
)

@Composable
fun ImagePickerScreen(
    onImagesSelected: (List<File>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val items = mutableListOf<MediaItem>()
            
            // Images
            val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol)
                    items.add(MediaItem(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), path))
                }
            }
            
            withContext(Dispatchers.Main) {
                mediaItems = items
            }
        }
    }

    ScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    ) {
                        Text("选取图片", color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                // Group into 3 columns
                val rows = mediaItems.chunked(3)
                items(rows.size) { rowIndex ->
                    val row = rows[rowIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { item ->
                            MediaTile(
                                item = item,
                                isSelected = selectedUris.contains(item.uri),
                                onToggle = {
                                    selectedUris = if (selectedUris.contains(item.uri)) {
                                        selectedUris - item.uri
                                    } else {
                                        selectedUris + item.uri
                                    }
                                },
                                modifier = Modifier.weight(1f).aspectRatio(1f)
                            )
                        }
                        // Fill remaining space
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Bouncy send button (appears at count > 0, pops only on 0->1 transition)
            AnimatedVisibility(
                visible = selectedUris.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = 400f
                    )
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = 300f
                    )
                ) + fadeOut()
            ) {
                val count = selectedUris.size
                FilledIconButton(
                    onClick = {
                        val files = mediaItems.filter { selectedUris.contains(it.uri) }.map { File(it.path) }
                        onImagesSelected(files)
                    },
                    shapes = IconButtonDefaults.animatedShapes(),
                    modifier = Modifier.height(48.dp).fillMaxWidth(if (count > 1) 0.6f else 0.5f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Confirm",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (count > 1) "发送($count)" else "发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    item: MediaItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onToggle() }
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .size(120)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(2.dp).size(16.dp)
                )
            }
        }
    }
}
