package com.qx.orbit.bili.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.presentation.ui.components.ReplyCard
import com.qx.orbit.bili.presentation.viewmodel.ReplyDetailViewModel
import com.qx.orbit.bili.presentation.ui.components.WysTimeText

@Composable
fun ReplyDetailScreen(
    reply: Reply,
    viewModel: ReplyDetailViewModel,
    navController: NavHostController
) {
    LaunchedEffect(reply) {
        viewModel.initData(reply)
    }

    val rootReply by viewModel.rootReply.collectAsState()
    val childReplies by viewModel.childReplies.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val focusRequester = remember { FocusRequester() }
    
    var showWriteReply by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<Reply?>(null) }
    val emotes by viewModel.emotes.collectAsState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState,
        modifier = Modifier.focusRequester(focusRequester)
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        ) {
            if (rootReply != null) {
                val root = rootReply!!
                item {
                    ListHeader {
                        Text(
                            text = "评论详情",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    ReplyCard(
                        reply = root,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec), // Don't animateItem for root as it might mess up header
                        navController = navController,
                        showReplyPreview = false,
                        isDetail = true,
                        replyType = if (root.isDynamic) ReplyApi.REPLY_TYPE_DYNAMIC else ReplyApi.REPLY_TYPE_VIDEO,
                        onRemove = { 
                            navController.popBackStack() 
                        },
                        onLikeClick = { viewModel.likeRootReply(root.liked) },
                        onReplyClick = { 
                            replyTarget = root
                            viewModel.loadEmotes()
                            showWriteReply = true 
                        }
                    )
                }
            }

            if (childReplies.isNotEmpty() || (rootReply?.childCount ?: 0) > 0) {

                /*item {
                    ListHeader {
                        val count = if (childReplies.size > (rootReply?.childCount ?: 0)) childReplies.size else rootReply?.childCount ?: 0
                        Text(
                            text = "相关回复(${formatCount(count)})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }*/

                items(childReplies.size) { index ->
                    if (index == childReplies.size - 1) {
                        LaunchedEffect(index) { viewModel.loadMore() }
                    }
                    ReplyCard(
                        reply = childReplies[index],
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.animateItem().transformedHeight(this, transformationSpec),
                        navController = navController,
                        showReplyPreview = false,
                        replyType = if (rootReply?.isDynamic == true) ReplyApi.REPLY_TYPE_DYNAMIC_CHILD else ReplyApi.REPLY_TYPE_VIDEO_CHILD,
                        onRemove = { viewModel.removeReplyLocally(childReplies[index]) },
                        onLikeClick = { viewModel.likeChildReply(childReplies[index].rpid, childReplies[index].liked) },
                        onReplyClick = { 
                            replyTarget = childReplies[index]
                            viewModel.loadEmotes()
                            showWriteReply = true
                        }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showWriteReply) {
        WriteReplyScreen(
            visible = showWriteReply,
            targetName = replyTarget?.sender?.name,
            emotes = emotes,
            onSend = { text ->
                viewModel.sendReply(text, replyTarget)
                showWriteReply = false
            },
            onClose = { showWriteReply = false }
        )
    }
}
