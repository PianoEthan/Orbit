package com.qx.orbit.bili.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.data.api.LoginApi
import com.qx.orbit.bili.presentation.viewmodel.HdQrCodeLoginViewModel
import com.qx.orbit.bili.presentation.viewmodel.HdQrStatus
import com.qx.orbit.bili.presentation.viewmodel.LoginState
import com.qx.orbit.bili.presentation.viewmodel.LoginViewModel

private val BiliPink = Color(0xFFFB7299)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSecondary = Color(0xFFAAAAAA)

/**
 * 统一登录页：Web 扫码 | HD 扫码 | Cookie 导入
 *
 * 三页横向滑动(无底部指示器),每页保持原本的 BiliPink 居中布局。
 * 任一种方式登录成功后回调 onLoginSuccess 并自动返回。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
    webLoginViewModel: LoginViewModel = viewModel(),
    hdLoginViewModel: HdQrCodeLoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit = {},
) {
    val webState by webLoginViewModel.state.collectAsState()
    val hdState by hdLoginViewModel.uiState.collectAsState()

    LaunchedEffect(webState) {
        if (webState is LoginState.Success) {
            onLoginSuccess()
            navController.popBackStack()
        }
    }
    LaunchedEffect(hdState.status) {
        if (hdState.status == HdQrStatus.LOGIN_SUCCESS) {
            onLoginSuccess()
            navController.popBackStack()
        }
    }

    val pagerState = rememberPagerState(pageCount = { 3 })

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> WebQrPage(webState, webLoginViewModel)
                1 -> HdQrPage(hdState, hdLoginViewModel)
                2 -> CookieImportPage(onLoginSuccess = {
                    onLoginSuccess()
                    navController.popBackStack()
                })
            }
        }
    }
}

// ── Web QR ──

@Composable
private fun WebQrPage(state: LoginState, vm: LoginViewModel) {
    LaunchedEffect(Unit) {
        if (state is LoginState.Initial) vm.startLogin()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                is LoginState.Initial, is LoginState.LoadingQRCode -> {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), color = BiliPink, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在获取二维码", fontSize = 13.sp, color = TextSecondary)
                }
                is LoginState.QRCodeReady -> {
                    QrContent(s.qrImage, s.message)
                }
                is LoginState.Error -> {
                    ErrorContent(s.error) { vm.startLogin() }
                }
                is LoginState.Success -> {
                    SuccessContent()
                }
            }
        }
    }
}

// ── HD QR ──

@Composable
private fun HdQrPage(state: com.qx.orbit.bili.presentation.viewmodel.HdQrCodeState, vm: HdQrCodeLoginViewModel) {
    LaunchedEffect(Unit) {
        if (state.status == HdQrStatus.REQUESTING) vm.requestQrCode()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.status) {
                HdQrStatus.REQUESTING -> {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), color = BiliPink, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在获取二维码", fontSize = 13.sp, color = TextSecondary)
                }
                HdQrStatus.WAITING -> {
                    QrContent(
                        qrImage = state.qrCodeUrl?.let { LoginApi.generateQRCodeBitmap(it, 300) },
                        message = "使用哔哩哔哩官方 App 扫码",
                    )
                }
                HdQrStatus.SCANNED -> {
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(BiliPink),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Check, "Scanned", tint = Color.White) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("请在手机上确认登录", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BiliPink, textAlign = TextAlign.Center)
                }
                HdQrStatus.EXPIRED -> ErrorContent("二维码已过期") { vm.requestQrCode() }
                HdQrStatus.ERROR -> ErrorContent(state.error ?: "加载失败") { vm.requestQrCode() }
                HdQrStatus.LOGIN_SUCCESS -> SuccessContent()
            }
        }
    }
}

// ── Cookie 导入 ──

@Composable
private fun CookieImportPage(onLoginSuccess: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Cookie 导入",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(error!!, fontSize = 11.sp, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                placeholder = { Text("粘贴 cookie JSON", fontSize = 11.sp, color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BiliPink,
                    cursorColor = BiliPink,
                    unfocusedBorderColor = TextSecondary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { /* by button */ }),
                singleLine = false,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    runCatching {
                        val data = com.google.gson.Gson().fromJson(text, CookieImportData::class.java)
                        requireNotNull(data.cookies) { "缺少 cookies" }
                        data.cookies
                    }.onSuccess { cookies ->
                        cookies.forEach { com.qx.orbit.bili.data.remote.CookieManager.putCookie(it.name, it.value) }
                        onLoginSuccess()
                    }.onFailure { e -> error = e.message ?: "解析失败" }
                },
                enabled = text.isNotBlank() && error == null,
                colors = ButtonDefaults.buttonColors(containerColor = BiliPink),
                modifier = Modifier.height(36.dp).widthIn(min = 80.dp),
            ) { Text("导入", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

// ── 共用组件 ──

@Composable
private fun QrContent(qrImage: android.graphics.Bitmap?, message: String) {
    val isScanned = message.contains("已扫码") || message.contains("手机上")

    Text("扫码登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(modifier = Modifier.height(6.dp))

    if (isScanned) {
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(BiliPink),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, "Scanned", tint = Color.White) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BiliPink, textAlign = TextAlign.Center)
    } else {
        Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            if (qrImage != null) {
                Image(bitmap = qrImage.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BiliPink, strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(containerColor = BiliPink),
        modifier = Modifier.height(36.dp).widthIn(min = 80.dp),
    ) { Text("重试", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun SuccessContent() {
    Text("登录成功", color = BiliPink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Text("正在跳转...", fontSize = 12.sp, color = TextSecondary)
}

private data class CookieImportData(
    val cookies: List<CookieImportItem>? = null,
)
private data class CookieImportItem(
    val name: String = "",
    val value: String = "",
)
