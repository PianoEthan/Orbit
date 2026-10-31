package com.qx.orbit.bili.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ReplyPaginationEffect(
    listState: TransformingLazyColumnState,
    lastReplyId: Long?,
    enabled: Boolean = true,
    canLoad: Boolean,
    onLoadMore: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentCanLoad by rememberUpdatedState(canLoad)
    val currentLastId by rememberUpdatedState(lastReplyId)
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                val lastId = currentLastId
                lastId to (currentEnabled && currentCanLoad && lastId != null &&
                    listState.layoutInfo.visibleItems.any { it.key == lastId })
            }.distinctUntilChanged().collect { (_, shouldLoad) ->
                if (shouldLoad) currentLoadMore()
            }
        }
    }
}
