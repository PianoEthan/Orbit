package com.qx.orbit.bili.presentation.ui.components

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.qx.orbit.bili.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
fun ImageViewerDialog(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (imageUrls.isEmpty()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showMenuForIndex by remember { mutableStateOf<Int?>(null) }
    var useOriginalQuality by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    Dialog(
        visible = true,
        onDismissRequest = onDismiss
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, imageUrls.lastIndex),
            pageCount = { imageUrls.size }
        )

        var currentScale by remember(pagerState.currentPage) { mutableFloatStateOf(1f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = currentScale <= 1.05f
            ) { page ->
                val baseDialogUrl = imageUrls[page]
                val originalUrl = if (baseDialogUrl.contains("@")) {
                    baseDialogUrl.substringBeforeLast("@")
                } else {
                    baseDialogUrl
                }

                val dialogUrl = if (useOriginalQuality) {
                    originalUrl
                } else {
                    if (!baseDialogUrl.contains("@")) "$baseDialogUrl@1024w.webp" else baseDialogUrl
                }

                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                },
                                onDoubleTap = {
                                    scale = if (scale > 1.2f) 1f else 2.5f
                                    offset = Offset.Zero
                                    currentScale = scale
                                },
                                onLongPress = {
                                    showMenuForIndex = page
                                }
                            )
                        }
                        .pointerInput(page) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = nextScale
                                currentScale = nextScale
                                if (scale > 1f) {
                                    val maxOffsetX = (scale - 1f) * 180f
                                    val maxOffsetY = (scale - 1f) * 180f
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = dialogUrl,
                        contentDescription = "图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "加载失败",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (imageUrls.size > 1) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1}/${imageUrls.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = { showMenuForIndex = pagerState.currentPage },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (imageUrls.size > 1) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                ) {
                    HorizontalPageIndicator(
                        pagerState = pagerState
                    )
                }
            }
        }
    }

    val activeIndex = showMenuForIndex
    if (activeIndex != null) {
        val currentUrl = imageUrls.getOrNull(activeIndex) ?: ""
        val menuItems = listOf(
            WysActionMenuItem(
                label = if (useOriginalQuality) "已开启原图 (切换标清)" else "查看高清原图",
                icon = if (useOriginalQuality) Icons.Default.HighQuality else Icons.Default.Image,
                onClick = {
                    useOriginalQuality = !useOriginalQuality
                    showMenuForIndex = null
                    RoundToast.show(context, if (useOriginalQuality) "已切换为原图模式" else "已切换为标清模式")
                }
            ),
            WysActionMenuItem(
                label = "保存图片到相册",
                icon = Icons.Default.Save,
                onClick = {
                    showMenuForIndex = null
                    val saveUrl = if (currentUrl.contains("@")) currentUrl.substringBeforeLast("@") else currentUrl
                    if (saveUrl.isBlank()) return@WysActionMenuItem

                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val ext = when {
                                    saveUrl.endsWith(".png", true) -> "png"
                                    saveUrl.endsWith(".webp", true) -> "webp"
                                    else -> "jpg"
                                }
                                val mime = "image/$ext"
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Orbit_${System.currentTimeMillis()}.$ext")
                                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Orbit")
                                }

                                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                if (uri != null) {
                                    val request = Request.Builder()
                                        .url(saveUrl)
                                        .header("Referer", "https://www.bilibili.com/")
                                        .build()
                                    HttpClient.client.newCall(request).execute().use { response ->
                                        if (response.isSuccessful) {
                                            val body = response.body
                                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                                body.byteStream().copyTo(output)
                                            }
                                            withContext(Dispatchers.Main) {
                                                RoundToast.show(context, "已保存到相册")
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                RoundToast.show(context, "保存失败: HTTP ${response.code}")
                                            }
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        RoundToast.show(context, "保存失败: 无法创建文件")
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    RoundToast.show(context, "保存失败: ${e.message}")
                                }
                            }
                        }
                    }
                }
            )
        )

        WysActionMenu(
            show = true,
            title = "图片操作",
            items = menuItems,
            onDismissRequest = { showMenuForIndex = null }
        )
    }
}
