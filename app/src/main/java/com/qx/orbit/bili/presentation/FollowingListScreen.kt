package com.qx.orbit.bili.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.People
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.ui.components.WysActionMenu
import com.qx.orbit.bili.presentation.ui.components.WysActionMenuItem
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.FollowingListViewModel
import kotlinx.coroutines.launch

@Composable
fun FollowingListScreen(
    mid: Long,
    navController: NavController,
    viewModel: FollowingListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    val isOwnList = mid > 0L && mid == CookieManager.getMid()
    var showGroups by rememberSaveable(mid, isOwnList) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val selectedTag = state.tags.firstOrNull { it.tagid == state.selectedTagId }

    LaunchedEffect(mid, isOwnList) { viewModel.loadUser(mid, isOwnList) }

    fun selectGroup(tagId: Int?) {
        viewModel.selectTag(tagId)
        coroutineScope.launch { listState.scrollToItem(0) }
    }

    ScreenScaffold(scrollState = listState, timeText = { WysTimeText() }) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
        ) {
            item {
                ListHeader { Text("关注列表", color = MaterialTheme.colorScheme.primary) }
            }
            if (isOwnList) {
                item {
                    Button(
                        onClick = { showGroups = true },
                        enabled = !state.isLoadingTags,
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text("关注分组", maxLines = 1) },
                        secondaryLabel = {
                            Text(
                                if (state.isLoadingTags) "正在加载分组" else selectedTag?.let { "${it.name} (${it.count})" } ?: "全部关注",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
                state.tagsError?.let { message ->
                    item {
                        Button(
                            onClick = viewModel::reloadTags,
                            modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            label = { Text("重试加载分组") },
                            secondaryLabel = { Text(message) }
                        )
                    }
                }
            }
            itemsIndexed(state.users, key = { _, user -> user.mid }) { index, user ->
                if (index >= state.users.size - 3 && state.hasMore && state.error == null) {
                    LaunchedEffect(state.users.size, index) { viewModel.loadMore() }
                }
                Button(
                    onClick = { navController.navigate("user_space/${user.mid}") },
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    icon = {
                        UserAvatar(
                            avatarUrl = user.avatar,
                            officialRole = user.official,
                            isVip = user.vip_role > 0,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    label = {
                        UserNameText(
                            name = user.name,
                            isVip = user.vip_role > 0,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(user.sign.ifBlank { "暂无签名" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                )
            }
            when {
                mid <= 0L -> item { Text("请先登录", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                state.error != null -> item {
                    Button(
                        onClick = viewModel::loadMore,
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        label = { Text("重试") },
                        secondaryLabel = { Text(state.error.orEmpty()) }
                    )
                }
                state.users.isEmpty() -> item {
                    Text(
                        if (state.selectedTagId == null) "暂无关注" else "该分组暂无关注",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (mid > 0L && !state.isLoading && state.error == null) {
                item {
                    Button(
                        onClick = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    ) { Text("刷新") }
                }
            }
        }
    }

    if (isOwnList) {
        WysActionMenu(
            show = showGroups,
            title = "关注分组",
            items = listOf(
                WysActionMenuItem(
                    label = "全部关注",
                    icon = if (state.selectedTagId == null) Icons.Default.Check else Icons.Default.People,
                    onClick = { selectGroup(null) }
                )
            ) + state.tags.map { tag ->
                WysActionMenuItem(
                    label = "${tag.name} (${tag.count})",
                    icon = if (state.selectedTagId == tag.tagid) Icons.Default.Check else Icons.Default.Folder,
                    onClick = { selectGroup(tag.tagid) }
                )
            },
            onDismissRequest = { showGroups = false }
        )
    }
}
