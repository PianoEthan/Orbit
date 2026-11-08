package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitCancellation
import com.qx.orbit.bili.core.api.CoreQrPollStatus

@Composable
internal fun VisionLoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: VisionLoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.state.collectAsState()
    val onSuccess by rememberUpdatedState(onLoginSuccess)
    val refreshFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(state.loading, state.status == CoreQrPollStatus.EXPIRED, inputMode.inputMode) {
        if (!state.loading && state.result == null && inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            refreshFocus.requestFocus()
        }
    }
    val bitmap = remember(state.code?.url) { state.code?.url?.let { generateQrCodeBitmap(it, 360) } }
    BackHandler(onBack = onBack)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.start()
            try { awaitCancellation() } finally { viewModel.stop() }
        }
    }
    LaunchedEffect(state.result) {
        state.result?.let { result ->
            VisionUserSession.onLoginSuccess(context, result.cookies, result.mid)
            onSuccess()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                val qrSize = if (wide) 220.dp else 200.dp
                val qr: @Composable () -> Unit = {
                    Box(Modifier.size(qrSize).background(Color.White), contentAlignment = Alignment.Center) {
                        when {
                            state.loading -> CircularProgressIndicator()
                            bitmap != null && state.status != CoreQrPollStatus.EXPIRED -> Image(bitmap.asImageBitmap(), "登录二维码", Modifier.fillMaxSize().padding(8.dp))
                            else -> Text("请刷新二维码", color = Color.Black, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                val instructions: @Composable () -> Unit = {
                    Column(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("扫码登录", style = MaterialTheme.typography.headlineSmall)
                        Text(state.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        FilledTonalButton(onClick = viewModel::refresh, enabled = !state.loading && state.result == null,
                            modifier = Modifier.focusRequester(refreshFocus).heightIn(min = 56.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("刷新二维码")
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        qr()
                        Box(Modifier.weight(1f, fill = false)) { instructions() }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        qr()
                        instructions()
                    }
                }
            }
        }
    }
}
