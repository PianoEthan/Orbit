package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreArticleBlock
import com.qx.orbit.bili.core.api.CoreArticleTextKind

@Composable
internal fun VisionArticleScreen(
    id: String,
    isOpus: Boolean,
    onBack: () -> Unit,
    onImages: (List<String>, Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: VisionArticleViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val backFocus = remember { FocusRequester() }
    var confirmingCoin by remember { mutableStateOf(false) }
    VisionInitialFocus(backFocus, state.article?.id)
    BackHandler(onBack = onBack)
    LaunchedEffect(id, isOpus, cookie) { viewModel.open(id, isOpus, cookie) }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(if (isOpus) "图文" else "专栏", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 840.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        state.error != null -> {
                            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            FilledTonalButton(onClick = { viewModel.open(id, isOpus, cookie) }) { Text("重试") }
                        }
                        state.article != null -> {
                            val article = state.article!!
                            if (article.banner.isNotBlank()) {
                                AsyncImage(article.banner, null, Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                            }
                            if (article.title.isNotBlank()) Text(article.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(article.avatar, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                Column(Modifier.weight(1f)) {
                                    Text(article.author.ifBlank { "作者" }, style = MaterialTheme.typography.titleSmall)
                                    if (article.published.isNotBlank()) Text(article.published, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (article.authorMid > 0) TextButton(onClick = { onOpenUser(article.authorMid) }, modifier = Modifier.heightIn(min = visionTouchMinHeight())) { Text("作者主页") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("阅读 ${formatVisionCount(article.stats.view.toInt())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("点赞 ${formatVisionCount(article.stats.like.toInt())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("回复 ${formatVisionCount(article.stats.reply.toInt())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("收藏 ${formatVisionCount(article.stats.favorite.toInt())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                            ArticleBlocks(article.blocks, onImages)
                            if (!isOpus) {
                                HorizontalDivider()
                                val cv = id.toLongOrNull() ?: 0L
                                if (!VisionUserSession.isLoggedIn) {
                                    Text("登录后可点赞、投币或收藏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(onClick = { viewModel.like(cv, cookie, VisionUserSession.csrf) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) {
                                            Text(if (state.liked) "已点赞 · 取消" else "点赞")
                                        }
                                        FilledTonalButton(onClick = { confirmingCoin = true }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) { Text("投币") }
                                        FilledTonalButton(onClick = { viewModel.favorite(cv, cookie, VisionUserSession.csrf) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) {
                                            Text(if (state.favorited) "已收藏 · 取消" else "收藏")
                                        }
                                    }
                                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmingCoin) AlertDialog(
        onDismissRequest = { confirmingCoin = false },
        title = { Text("投 1 枚硬币？") },
        text = { Text("将使用当前账号向该专栏投 1 枚硬币，投币后不可撤回。") },
        confirmButton = {
            TextButton(onClick = {
                confirmingCoin = false
                viewModel.coin(id.toLongOrNull() ?: 0L, state.article?.authorMid ?: 0L, cookie, VisionUserSession.csrf)
            }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = { confirmingCoin = false }) { Text("取消") } },
    )
}

@Composable
private fun ArticleBlocks(blocks: List<CoreArticleBlock>, onImages: (List<String>, Int) -> Unit) {
    val images = blocks.filterIsInstance<CoreArticleBlock.Image>().map { it.url }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        blocks.forEach { block ->
            when (block) {
                is CoreArticleBlock.Text -> when (block.kind) {
                    CoreArticleTextKind.HEADING -> Text(block.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    CoreArticleTextKind.QUOTE -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        Text(block.text, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                    CoreArticleTextKind.PARAGRAPH -> Text(block.text, style = MaterialTheme.typography.bodyLarge)
                }
                is CoreArticleBlock.Image -> {
                    val index = images.indexOf(block.url).coerceAtLeast(0)
                    VisionImageTile(block.url, "查看图片", Modifier.fillMaxWidth().heightIn(max = 560.dp)) { onImages(images, index) }
                }
                CoreArticleBlock.Divider -> HorizontalDivider()
            }
        }
    }
}
