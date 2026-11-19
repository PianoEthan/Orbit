package com.qx.orbit.bili.presentation.ui.components

import android.content.ContentValues
import android.graphics.drawable.Animatable
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.math.exp
import kotlin.math.hypot

private const val ROTARY_IMAGE_ZOOM_SENSITIVITY = 0.005f
private const val DOUBLE_TAP_ANIMATION_DURATION_MS = 260
private const val DOUBLE_TAP_IMAGE_SCALE = 2.5f
private const val MIN_IMAGE_SCALE = 0.8f
private const val MAX_IMAGE_SCALE = 5.0f

internal data class FittedImageSize(
    val width: Float,
    val height: Float
)

internal fun calculateFittedImageSize(viewportSize: IntSize, imageSize: IntSize): FittedImageSize {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || imageSize.width <= 0 || imageSize.height <= 0) {
        return FittedImageSize(viewportSize.width.toFloat(), viewportSize.height.toFloat())
    }
    val widthScale = viewportSize.width.toFloat() / imageSize.width.toFloat()
    val heightScale = viewportSize.height.toFloat() / imageSize.height.toFloat()
    val fitScale = minOf(widthScale, heightScale)
    return FittedImageSize(
        width = imageSize.width * fitScale,
        height = imageSize.height * fitScale
    )
}

internal fun calculateInitialRoundScale(
    isRoundScreen: Boolean,
    viewportSize: IntSize,
    fittedSize: FittedImageSize
): Float {
    if (!isRoundScreen || viewportSize.width <= 0 || viewportSize.height <= 0) return 1f
    val diagonal = hypot(fittedSize.width, fittedSize.height)
    if (diagonal <= 0f) return 1f
    val diameter = minOf(viewportSize.width, viewportSize.height).toFloat()
    return (diameter / diagonal).coerceAtMost(1f)
}

internal fun clampTranslation(
    translation: Offset,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float
): Offset {
    if (scale <= 1f || viewportWidth <= 0f || viewportHeight <= 0f) return Offset.Zero
    val maxOffsetX = (scale - 1f) * viewportWidth / 2f
    val maxOffsetY = (scale - 1f) * viewportHeight / 2f
    return Offset(
        x = translation.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = translation.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

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

                ZoomableImagePage(
                    url = dialogUrl,
                    isCurrentPage = pagerState.currentPage == page,
                    onScaleChanged = { scale ->
                        if (pagerState.currentPage == page) {
                            currentScale = scale
                        }
                    },
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onLongPress = {
                        showMenuForIndex = page
                    }
                )
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

@Composable
private fun ZoomableImagePage(
    url: String,
    isCurrentPage: Boolean,
    onScaleChanged: (Float) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isRound = LocalScreenRound.current

    var retryTrigger by remember { mutableStateOf(0) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    var scale by remember(url) { mutableFloatStateOf(1f) }
    var translation by remember(url) { mutableStateOf(Offset.Zero) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(scale) {
        onScaleChanged(scale)
    }

    val updateTransform: (Float, Offset) -> Unit = { zoomChange, panChange ->
        val nextScale = (scale * zoomChange).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
        scale = nextScale
        translation = if (scale > 1f) {
            clampTranslation(
                translation = translation + panChange,
                scale = scale,
                viewportWidth = viewportSize.width.toFloat(),
                viewportHeight = viewportSize.height.toFloat()
            )
        } else {
            Offset.Zero
        }
    }

    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        updateTransform(zoomChange, panChange)
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .setParameter("retry", retryTrigger)
            .build()
    )

    val painterState = painter.state

    // Handle GIF / WebP animated drawables
    DisposableEffect(painterState) {
        val animatable = (painterState as? AsyncImagePainter.State.Success)?.result?.drawable as? Animatable
        animatable?.start()
        onDispose {
            animatable?.stop()
        }
    }

    // Capture intrinsic image dimensions
    LaunchedEffect(painterState) {
        if (painterState is AsyncImagePainter.State.Success) {
            val drawable = painterState.result.drawable
            val w = drawable.intrinsicWidth
            val h = drawable.intrinsicHeight
            if (w > 0 && h > 0) {
                imageSize = IntSize(w, h)
            }
        }
    }

    val fittedSize = remember(viewportSize, imageSize) {
        calculateFittedImageSize(viewportSize, imageSize)
    }
    val initialRoundScale = remember(isRound, viewportSize, fittedSize) {
        calculateInitialRoundScale(isRound, viewportSize, fittedSize)
    }
    val effectiveScale = initialRoundScale * scale

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewportSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                val zoomChange = exp(event.verticalScrollPixels * ROTARY_IMAGE_ZOOM_SENSITIVITY)
                updateTransform(zoomChange, Offset.Zero)
                true
            }
            .transformable(
                state = transformableState,
                canPan = { scale > 1.05f }
            )
            .pointerInput(url, viewportSize, imageSize, scale, translation) {
                detectTapGestures(
                    onTap = { position ->
                        val halfWidth = fittedSize.width * effectiveScale / 2f
                        val halfHeight = fittedSize.height * effectiveScale / 2f
                        val centerX = viewportSize.width / 2f + translation.x
                        val centerY = viewportSize.height / 2f + translation.y
                        val insideImage = position.x in (centerX - halfWidth)..(centerX + halfWidth) &&
                            position.y in (centerY - halfHeight)..(centerY + halfHeight)
                        onTap()
                    },
                    onDoubleTap = {
                        val currentScale = scale
                        val targetScale = if (scale > 1.2f) 1f else DOUBLE_TAP_IMAGE_SCALE
                        coroutineScope.launch {
                            transformableState.animateBy(
                                zoomFactor = targetScale / currentScale,
                                panOffset = -translation,
                                rotationDegrees = 0f,
                                zoomAnimationSpec = tween(DOUBLE_TAP_ANIMATION_DURATION_MS),
                                panAnimationSpec = tween(DOUBLE_TAP_ANIMATION_DURATION_MS)
                            )
                            scale = targetScale
                            translation = Offset.Zero
                        }
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when (painterState) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            }
            is AsyncImagePainter.State.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "图片加载失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { retryTrigger++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重试",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(text = " 重试")
                        }
                    }
                }
            }
            else -> {
                Image(
                    painter = painter,
                    contentDescription = "图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            translationX = translation.x
                            translationY = translation.y
                            transformOrigin = TransformOrigin.Center
                        }
                )
            }
        }
    }
}
