package com.qx.orbit.bili.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.data.account.AccountSummary
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.MainActivity
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior

@Composable
fun AccountManagementScreen(navController: NavController) {
    val accountState by CookieManager.accountState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    val context = LocalContext.current

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            val edgeButtonSize = EdgeButtonSize.Medium
            EdgeButton(
                onClick = { navController.navigate("login") },
                modifier = Modifier.fillMaxWidth(),
                buttonSize = edgeButtonSize,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(EdgeButtonDefaults.iconSizeFor(edgeButtonSize)),
                    )
                    Text(
                        text = "添加账号",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        edgeButtonSpacing = 8.dp,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) {
                        SurfaceTransformation(transformationSpec)
                    } else {
                        null
                    },
                ) {
                    Text(
                        text = "账号管理",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(accountState.accounts, key = AccountSummary::mid) { account ->
                val active = account.mid == accountState.activeMid
                Button(
                    onClick = {
                        if (!active && CookieManager.switchAccount(account.mid)) {
                            restartApp(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        contentColor = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) {
                        SurfaceTransformation(transformationSpec)
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        UserAvatar(
                            avatarUrl = account.avatarUrl,
                            officialRole = 0,
                            modifier = Modifier.size(36.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Text(
                                text = "UID ${account.mid}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
