package com.qx.orbit.bili.presentation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
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
import com.qx.orbit.bili.presentation.ui.components.PrivateSessionCard
import com.qx.orbit.bili.presentation.ui.components.UnreadBadge
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.ui.components.rememberAdaptiveSurfaceTransformation
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.MessageCenterViewModel
import com.qx.orbit.bili.presentation.viewmodel.NotificationType

@Composable
fun MessageCenterScreen(
    navController: NavController,
    viewModel: MessageCenterViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState,
        modifier = Modifier.fillMaxSize()
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
        ) {
            item {
                ListHeader(
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                    transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = "消息中心",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item {
                NotificationCategoryButton(
                    title = NotificationType.Reply.title,
                    unread = state.unread.reply,
                    icon = Icons.Default.ChatBubble,
                    onClick = { navController.navigate("notification/${NotificationType.Reply.routeValue}") },
                    transformation = rememberAdaptiveSurfaceTransformation(transformationSpec),
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                )
            }
            item {
                NotificationCategoryButton(
                    title = NotificationType.Like.title,
                    unread = state.unread.like,
                    icon = Icons.Default.Favorite,
                    onClick = { navController.navigate("notification/${NotificationType.Like.routeValue}") },
                    transformation = rememberAdaptiveSurfaceTransformation(transformationSpec),
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                )
            }
            item {
                NotificationCategoryButton(
                    title = NotificationType.At.title,
                    unread = state.unread.at,
                    icon = Icons.Default.AlternateEmail,
                    onClick = { navController.navigate("notification/${NotificationType.At.routeValue}") },
                    transformation = rememberAdaptiveSurfaceTransformation(transformationSpec),
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                )
            }
            item {
                NotificationCategoryButton(
                    title = NotificationType.System.title,
                    unread = state.unread.system,
                    icon = Icons.Default.Notifications,
                    onClick = { navController.navigate("notification/${NotificationType.System.routeValue}") },
                    transformation = rememberAdaptiveSurfaceTransformation(transformationSpec),
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                )
            }
            item {
                Text(
                    text = "私信",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .adaptiveTransformedHeight(this, transformationSpec)
                )
            }

            if (state.errorMessage != null && state.sessions.isEmpty()) {
                item {
                    MessageCenterStatus(
                        message = state.errorMessage.orEmpty(),
                        modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                    )
                }
            } else if (!state.isLoading && state.sessions.isEmpty()) {
                item {
                    MessageCenterStatus(
                        message = "暂无私信会话",
                        modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                    )
                }
            }

            items(
                items = state.sessions,
                key = { session -> session.talkerUid }
            ) { session ->
                PrivateSessionCard(
                    session = session,
                    user = state.users[session.talkerUid],
                    onClick = { navController.navigate("private_chat/${session.talkerUid}") },
                    transformation = rememberAdaptiveSurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .animateItem()
                )
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .adaptiveTransformedHeight(this, transformationSpec),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                item {
                    Button(
                        onClick = viewModel::refresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("刷新消息")
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun NotificationCategoryButton(
    title: String,
    unread: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    transformation: SurfaceTransformation?,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        transformation = transformation
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = title, modifier = Modifier.weight(1f))
            if (unread > 0) UnreadBadge(unread)
        }
    }
}

@Composable
private fun MessageCenterStatus(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
