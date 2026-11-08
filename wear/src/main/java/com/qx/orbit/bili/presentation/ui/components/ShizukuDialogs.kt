package com.qx.orbit.bili.presentation.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.util.ShizukuUtils

@Composable
fun ShizukuActivationDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    onShowNotInstalled: () -> Unit
) {
    WysAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "Shizuku 未运行",
        content = { Text("为了获取所有文件访问权限，需要运行 Shizuku。请打开 Shizuku 并启动服务。") },
        onConfirm = {
            onDismissRequest()
            if (!ShizukuUtils.openShizukuManager(context)) {
                onShowNotInstalled()
            }
        }
    )
}

@Composable
fun ShizukuNotInstalledDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    WysAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "未安装 Shizuku",
        content = { Text("未安装 Shizuku 管理器，请先安装 Shizuku。") },
        onConfirm = onDismissRequest
    )
}

@Composable
fun ShizukuPermissionDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    onConfirmAuth: () -> Unit
) {
    WysAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "所有文件访问权限",
        content = { Text("由于系统限制，默认配置下无法缓存弹幕和字幕文件。是否通过 Shizuku 授予 Orbit 所有文件访问权限？\n\n这需要在弹出的 Shizuku 窗口中选择“始终允许”。") },
        onConfirm = onConfirmAuth
    )
}
