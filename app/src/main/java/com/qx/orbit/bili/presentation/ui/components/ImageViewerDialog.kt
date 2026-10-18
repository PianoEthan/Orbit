package com.qx.orbit.bili.presentation.ui.components

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.MoreVert
import androidx.wear.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import androidx.compose.foundation.layout.Arrangement
import androidx.wear.compose.material3.MaterialTheme
import com.qx.orbit.bili.presentation.ui.components.RoundToast

@Composable
fun ImageViewerDialog(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showMenuForUrl by remember { mutableStateOf<String?>(null) }
    var useOriginalQuality by remember { mutableStateOf(false) }

    Dialog(
        visible = imageUrls.isNotEmpty(),
        onDismissRequest = onDismiss
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { imageUrls.size })
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
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
                
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { showMenuForUrl = dialogUrl }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = dialogUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    )
                }
            }
            if (imageUrls.size > 1) {
                HorizontalPageIndicator(
                    pagerState = pagerState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                )
            }
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(24.dp), tint = Color.White)
            }
            
            Button(
                onClick = { 
                    val currentUrl = imageUrls[pagerState.currentPage]
                    val originalUrl = if (currentUrl.contains("@")) {
                        currentUrl.substringBeforeLast("@")
                    } else {
                        currentUrl
                    }
                    showMenuForUrl = if (useOriginalQuality) originalUrl else {
                        if (!currentUrl.contains("@")) "$currentUrl@1024w.webp" else currentUrl
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp), tint = Color.White)
            }
        }
    }

    Dialog(
        visible = showMenuForUrl != null,
        onDismissRequest = { showMenuForUrl = null }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        icon = { Icon(Icons.Default.Image, contentDescription = null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (useOriginalQuality) Color.DarkGray else Color(0xFF333333)
                        ),
                        onClick = {
                            useOriginalQuality = !useOriginalQuality
                            showMenuForUrl = null
                        }
                    ) {
                        Text(text = if (useOriginalQuality) "已开启原图" else "查看原图")
                    }
                }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        icon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = {
                            val url = showMenuForUrl ?: return@Button
                            showMenuForUrl = null
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        // Save original URL always when saving
                                        val saveUrl = if (url.contains("@")) url.substringBeforeLast("@") else url
                                        
                                        val ext = if (saveUrl.endsWith(".png", true)) "png" else if (saveUrl.endsWith(".webp", true)) "webp" else "jpg"
                                        val mime = "image/$ext"
                                        val contentValues = ContentValues().apply {
                                            put(MediaStore.MediaColumns.DISPLAY_NAME, "Orbit_${System.currentTimeMillis()}.$ext")
                                            put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Orbit")
                                        }
                                        
                                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                        if (uri != null) {
                                            URL(saveUrl).openStream().use { input ->
                                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                RoundToast.show(context, "已保存到相册")
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
                    ) {
                        Text(text = "保存图片")
                    }
                }
            }
        }
    }
}
