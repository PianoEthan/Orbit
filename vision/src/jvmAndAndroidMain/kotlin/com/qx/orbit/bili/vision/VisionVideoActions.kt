package com.qx.orbit.bili.vision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun VisionVideoActions(viewModel: VisionDetailViewModel, onLogin: () -> Unit) {
    val pending by viewModel.interactionPending.collectAsState()
    val message by viewModel.interactionMessage.collectAsState()
    val relation by viewModel.relation.collectAsState()
    val relationLoading by viewModel.relationLoading.collectAsState()
    val relationError by viewModel.relationError.collectAsState()
    var confirmation by remember { mutableStateOf<String?>(null) }
    var choosingFavorite by remember { mutableStateOf(false) }
    val cookie = VisionUserSession.cookie
    LaunchedEffect(cookie) { confirmation = null; choosingFavorite = false }
    val ready = !pending && !relationLoading && !relationError && relation != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!VisionUserSession.isLoggedIn) {
            TextButton(onClick = onLogin, modifier = Modifier.heightIn(min = 48.dp)) { Text("登录后点赞、投币或三连") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.like(cookie, VisionUserSession.csrf) }, enabled = ready, modifier = Modifier.visionFocusBorder()) {
                    Text(if (relation?.liked == true) "已点赞 · 取消" else "点赞")
                }
                FilledTonalButton(onClick = { confirmation = "coin" }, enabled = ready && (relation?.coinCount ?: 0) < 2, modifier = Modifier.visionFocusBorder()) {
                    Text(if ((relation?.coinCount ?: 0) > 0) "已投 ${relation?.coinCount} 枚" else "投币")
                }
                FilledTonalButton(onClick = { choosingFavorite = true }, enabled = ready, modifier = Modifier.visionFocusBorder()) { Text("收藏到…") }
                FilledTonalButton(onClick = { confirmation = "triple" }, enabled = ready && !(relation?.liked == true && relation?.favorited == true && (relation?.coinCount ?: 0) > 0), modifier = Modifier.visionFocusBorder()) {
                    Text(if (relation?.liked == true && relation?.favorited == true && (relation?.coinCount ?: 0) > 0) "已三连" else "一键三连")
                }
            }
            TextButton(onClick = { viewModel.addWatchLater(cookie, VisionUserSession.csrf) }, enabled = ready, modifier = Modifier.visionFocusBorder()) { Text("添加到稍后再看") }
            if (relation != null) Text(if (relation?.favorited == true) "已收藏" else "未收藏", style = MaterialTheme.typography.bodySmall)
            if (relationLoading) Text("正在同步账号操作状态", style = MaterialTheme.typography.bodySmall)
            if (relationError) Text("状态同步失败，暂不提交操作", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::refreshRelation, enabled = !pending && !relationLoading, modifier = Modifier.visionFocusBorder()) { Text("刷新操作状态") }
        }
        if (pending || relationLoading) LinearProgressIndicator()
        if (pending) Text("正在提交，请勿重复操作", style = MaterialTheme.typography.bodySmall)
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (choosingFavorite && VisionUserSession.isLoggedIn) {
        VisionFavoritePicker(cookie, VisionUserSession.mid,
            onDismiss = { choosingFavorite = false },
            onConfirm = { folder -> choosingFavorite = false; viewModel.favorite(folder, cookie, VisionUserSession.csrf) })
    }
    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (action == "coin") "投 1 枚硬币？" else "确认一键三连？") },
            text = { Text(if (action == "coin") "将使用当前账号向该视频再投 1 枚硬币，投币后不可撤回。" else "将使用当前账号点赞、投币并收藏到默认收藏夹。系统通常投 2 枚硬币，实际数量以接口结果为准，投币后不可撤回。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (action == "coin") viewModel.coin(cookie, VisionUserSession.csrf)
                    else viewModel.triple(cookie, VisionUserSession.csrf)
                }, enabled = ready) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } },
        )
    }
}
