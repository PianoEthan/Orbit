package com.qx.orbit.bili.presentation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.parseRichText
import com.qx.orbit.bili.util.fixCoverUrl
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.data.api.ReplyApi
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.qx.orbit.bili.data.model.ArticleInfo
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.presentation.ui.components.ImageViewerDialog
import com.qx.orbit.bili.presentation.ui.components.ReplyCard
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import com.qx.orbit.bili.presentation.viewmodel.ArticleDetailViewModel
import com.qx.orbit.bili.util.formatBiliTime
import com.qx.orbit.bili.util.formatCount
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    navController: NavHostController,
    commentRootId: Long = 0L,
    commentReplyId: Long = 0L,
    viewModel: ArticleDetailViewModel = viewModel()
) {
    val article by viewModel.article.collectAsState()
    val error by viewModel.error.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = if (commentRootId > 0L) 1 else 0,
        pageCount = { 2 }
    )
    val focusRequesters = remember { List(2) { FocusRequester() } }
    var showWriteReply by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<Reply?>(null) }
    val emotes by viewModel.emotes.collectAsState()

    LaunchedEffect(articleId, commentRootId, commentReplyId) {
        viewModel.loadArticle(articleId, commentRootId, commentReplyId)
    }

    LaunchedEffect(pagerState.currentPage) {
        try { focusRequesters[pagerState.currentPage].requestFocus() } catch (_: Exception) {}
    }

    ScreenScaffold(timeText = { WysTimeText() }) {
        val errorMsg = error
        when (article) {
            null if errorMsg == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            null -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMsg ?: "加载失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadArticle(articleId, commentRootId, commentReplyId) },
                            modifier = Modifier.size(width = 80.dp, height = 32.dp)
                        ) {
                            Text("重试", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            0 -> ArticleContentPage(article!!, navController, focusRequesters[0])
                            1 -> ArticleCommentsPage(
                                viewModel, navController, focusRequesters[1],
                                onReplyClick = { reply ->
                                    replyTarget = reply
                                    viewModel.loadEmotes()
                                    showWriteReply = true
                                },
                                onSendCommentClick = {
                                    replyTarget = null
                                    viewModel.loadEmotes()
                                    showWriteReply = true
                                }
                            )
                        }
                    }
                    HorizontalPageIndicator(
                        pagerState = pagerState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                    )
                }
                WriteReplyScreen(
                    visible = showWriteReply,
                    targetName = replyTarget?.sender?.name,
                    emotes = emotes,
                    onSend = { text, _ ->
                        viewModel.sendReply(text, replyTarget)
                        showWriteReply = false
                    },
                    onClose = { showWriteReply = false }
                )
            }
        }
    }
}

@Composable
fun ArticleContentPage(
    item: ArticleInfo,
    navController: NavHostController,
    focusRequester: FocusRequester
) {
    val listState = rememberTransformingLazyColumnState()
    val behavior = rememberSafeRotaryScrollableBehavior(listState)
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    var showImageDialog by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val segments = remember(item.content) { parseArticleHtml(item.content) }
    val context = LocalContext.current

    val allImages = remember(segments, item.banner) {
        val list = mutableListOf<String>()
        if (item.banner.isNotEmpty()) list.add(fixUrl(item.banner))
        segments.forEach { if (it is ArticleSegment.Image) list.add(fixUrl(it.url)) }
        list
    }

    if (showImageDialog != null) {
        ImageViewerDialog(
            imageUrls = showImageDialog!!.first,
            initialIndex = showImageDialog!!.second,
            onDismiss = { showImageDialog = null }
        )
    }

    TransformingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().rotaryScrollable(behavior, focusRequester),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp)
    , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
        if (item.title.isNotEmpty()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec).graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }.padding(vertical = 8.dp).clickable {
                    item.upInfo?.mid?.let { navController.navigate("user_space/$it") }
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                UserAvatar(
                    avatarUrl = item.upInfo?.avatar ?: "",
                    officialRole = item.upInfo?.official ?: 0,
                    modifier = Modifier.size(32.dp),
                    isVip = (item.upInfo?.vip_role ?: 0) > 0
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    UserNameText(
                        name = item.upInfo?.name ?: "",
                        isVip = (item.upInfo?.vip_role ?: 0) > 0,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (item.ctime > 0) formatBiliTime(item.ctime) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }

        if (item.banner.isNotEmpty()) {
            item {
                val fixedUrl = fixUrl(item.banner)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(fixedUrl).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec).graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }.height(160.dp).padding(vertical = 4.dp).clickable { showImageDialog = Pair(allImages, 0) },
                    contentScale = ContentScale.Crop,
                    loading = { Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A))) },
                    error = { Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A))) }
                )
            }
        }

        items(segments.size) { idx ->
            Box(
                modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec).graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
            ) {
                when (val seg = segments[idx]) {
                    is ArticleSegment.Image -> {
                        val fixedUrl = fixUrl(seg.url)
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context).data(fixedUrl).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showImageDialog = Pair(allImages, allImages.indexOf(fixedUrl).takeIf { it >= 0 } ?: 0) },
                            contentScale = ContentScale.Fit,
                            loading = { Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A))) },
                            error = { Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A))) }
                        )
                    }
                    is ArticleSegment.Text -> {
                        Text(
                            text = seg.text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                    is ArticleSegment.Heading -> {
                        Text(
                            text = seg.text,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                    is ArticleSegment.Link -> {
                        Text(
                            text = seg.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, fixUrl(seg.url).toUri())) } catch (_: Exception) {}
                            }
                        )
                    }
                    is ArticleSegment.Quote -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).padding(start = 8.dp)) {
                            Text(
                                text = seg.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        item {
            val stats = item.stats
            if (stats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec).graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("${formatCount(stats.view)}阅读", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("${formatCount(stats.like)}赞", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("${formatCount(stats.coin)}投币", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("${formatCount(stats.favorite)}收藏", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun ArticleCommentsPage(
    viewModel: ArticleDetailViewModel,
    navController: NavHostController,
    focusRequester: FocusRequester,
    onReplyClick: (Reply) -> Unit = {},
    onSendCommentClick: () -> Unit = {}
) {
    val replies by viewModel.replies.collectAsState()
    val replyCount by viewModel.replyCount.collectAsState()
    val focusedReplyId by viewModel.focusedReplyId.collectAsState()
    val isReplyLoading by viewModel.isReplyLoading.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    val behavior = rememberSafeRotaryScrollableBehavior(listState)
    val focusedReplyIndex = remember(replies, focusedReplyId) {
        replies.indexOfFirst { it.rpid == focusedReplyId }
    }

    LaunchedEffect(focusedReplyId, focusedReplyIndex) {
        if (focusedReplyId > 0L && focusedReplyIndex >= 0) {
            listState.animateScrollToItem(focusedReplyIndex + 2)
        }
    }

    TransformingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().rotaryScrollable(behavior, focusRequester),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp)
    , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
        item {
            ListHeader(
                modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
            ) {
                Text(
                    "评论(${formatCount(replyCount)})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Button(
                onClick = onSendCommentClick,
                modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                icon = {Icon(imageVector = Icons.Filled.Edit, contentDescription = null)},
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("发送评论", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        items(count = replies.size, key = { replies[it].rpid }) { index ->
            if (index == replies.size - 3) {
                LaunchedEffect(index) { viewModel.loadReplies() }
            }
            ReplyCard(
                reply = replies[index],
                highlighted = replies[index].rpid == focusedReplyId,
                transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                modifier = Modifier.animateItem().adaptiveTransformedHeight(this, transformationSpec),
                navController = navController,
                replyType = ReplyApi.REPLY_TYPE_ARTICLE,
                onRemove = { viewModel.removeReplyLocally(replies[index]) },
                onTopChanged = viewModel::updateReplyTop,
                onLikeClick = { viewModel.likeReply(replies[index].rpid) },
                onReplyClick = { onReplyClick(replies[index]) }
            )
        }
        if (isReplyLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

private fun fixUrl(url: String): String {
    val base = url.fixCoverUrl()
    // 如果已含 @ 裁剪参数则不再追加
    if (base.contains("@")) return base
    // B站 CDN 图片追加 @480w.webp 缩略后缀
    if (base.contains("hdslb.com") || base.contains("bfs/")) {
        return "$base@480w.webp"
    }
    return base
}

private sealed class ArticleSegment {
    data class Text(val text: String) : ArticleSegment()
    data class Image(val url: String) : ArticleSegment()
    data class Link(val text: String, val url: String) : ArticleSegment()
    data class Heading(val text: String) : ArticleSegment()
    data class Quote(val text: String) : ArticleSegment()
}

private fun parseArticleHtml(html: String): List<ArticleSegment> {
    if (html.isBlank()) return emptyList()
    val segments = mutableListOf<ArticleSegment>()
    val imgPattern = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""")
    val linkPattern = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    val headingPattern = Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.DOT_MATCHES_ALL)
    val quotePattern = Regex("""<blockquote[^>]*>(.*?)</blockquote>""", RegexOption.DOT_MATCHES_ALL)
    val tagStripper = Regex("""<[^>]+>""")
    val entityMap = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&nbsp;" to " ")

    fun stripEntities(s: String): String {
        var r = s
        for ((entity, char) in entityMap) r = r.replace(entity, char)
        return r.trim()
    }

    var remaining = html

    while (remaining.isNotEmpty()) {
        val nextImg = imgPattern.find(remaining)
        val nextLink = linkPattern.find(remaining)
        val nextHeading = headingPattern.find(remaining)
        val nextQuote = quotePattern.find(remaining)

        val earliest = listOfNotNull(
            nextImg?.let { it to "img" },
            nextLink?.let { it to "link" },
            nextHeading?.let { it to "heading" },
            nextQuote?.let { it to "quote" }
        ).minByOrNull { it.first.range.first }

        if (earliest == null) {
            val text = stripEntities(tagStripper.replace(remaining, "")).replace(Regex("""\n{3,}"""), "\n\n")
            if (text.isNotBlank()) segments.add(ArticleSegment.Text(text))
            break
        }

        val (match, type) = earliest
        val before = remaining.substring(0, match.range.first)
        val beforeText = stripEntities(tagStripper.replace(before, "")).replace(Regex("""\n{3,}"""), "\n\n")
        if (beforeText.isNotBlank()) segments.add(ArticleSegment.Text(beforeText))

        when (type) {
            "img" -> {
                val src = fixUrl(match.groupValues[1])
                if (src.isNotEmpty()) segments.add(ArticleSegment.Image(src))
            }
            "link" -> {
                val href = match.groupValues[1]
                val linkText = stripEntities(tagStripper.replace(match.groupValues[2], ""))
                if (linkText.isNotBlank()) segments.add(ArticleSegment.Link(linkText, href))
            }
            "heading" -> {
                val headingText = stripEntities(tagStripper.replace(match.groupValues[1], ""))
                if (headingText.isNotBlank()) segments.add(ArticleSegment.Heading(headingText))
            }
            "quote" -> {
                val quoteText = stripEntities(tagStripper.replace(match.groupValues[1], ""))
                if (quoteText.isNotBlank()) segments.add(ArticleSegment.Quote(quoteText))
            }
        }
        remaining = remaining.substring(match.range.last + 1)
    }

    return segments
}
