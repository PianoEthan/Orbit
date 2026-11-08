package com.qx.orbit.bili.presentation.about

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.runtime.Composable
import com.qx.orbit.bili.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScreenScaffoldDefaults.contentPadding
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.compose.foundation.text.ClickableText
import com.qx.orbit.bili.R
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.util.parseRichText
import androidx.wear.compose.material3.SurfaceTransformation


@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    var showBrowserError by remember { mutableStateOf(false) }
    val isRound = LocalScreenRound.current
    var showDonationDialog by remember { mutableStateOf(false) }

    ScreenScaffold(
        scrollState = listState,
        contentPadding = contentPadding
    ) { it ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = it,
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                ) {
                    Text(text = stringResource(R.string.about_software), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.W700)
                }
            }

            // 应用图标和名称
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                        .adaptiveTransformedHeight(this, transformationSpec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.orbit),
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(80.dp)
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                    .adaptiveTransformedHeight(this, transformationSpec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.W700)
                    Text(text = BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = Build.MODEL,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp).adaptiveTransformedHeight(this, transformationSpec)) }

            // 简介
            item {
                val uriHandler = LocalUriHandler.current
                val clipboardManager = LocalClipboardManager.current
                val descText = stringResource(R.string.about_description)
                val (annotatedDesc, _) = parseRichText(descText, emptyMap())
                fun openUrl(url: String) {
                    try {
                        uriHandler.openUri(url)
                    } catch (_: Exception) {
                        clipboardManager.setText(AnnotatedString(url))
                        RoundToast.show(context, "链接已复制到剪贴板")
                    }
                }
                TitleCard(
                    onClick = { },
                    title = { Text(stringResource(R.string.software_intro)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                ) {
                    ClickableText(
                        text = annotatedDesc,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { offset ->
                            annotatedDesc.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { openUrl(it.item) }
                            annotatedDesc.getStringAnnotations(tag = "VIDEO", start = offset, end = offset)
                                .firstOrNull()?.let { openUrl(it.item) }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp).adaptiveTransformedHeight(this, transformationSpec)) }

        // 开发者列表
            item {
                Text(
                    text = "开发人员名单",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                        .adaptiveTransformedHeight(this, transformationSpec)
                )
            }

            val developers = listOf(
                Triple("琴弦上的小医生", R.drawable.qinxian, "开发者") ,
                Triple("iamRJ", R.drawable.iam_rj, "贡献者")
            )

            items(developers) { (name, iconRes, desc) ->
                Button(
                    onClick = { showDonationDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    icon = {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = name,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    },
                    label = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
            // 捐赠按钮
            item {
                Button(
                    onClick = { showDonationDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    icon = {
                        Icon(Icons.Default.Coffee, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    },
                    label = {
                        Text(
                            text = "支持开发",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        DonationDialog(
            show = showDonationDialog,
            onDismissRequest = { showDonationDialog = false },
            onConfirm = { showDonationDialog = false }
        )
    }
}
