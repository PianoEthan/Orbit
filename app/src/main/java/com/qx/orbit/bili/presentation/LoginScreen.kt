package com.qx.orbit.bili.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import com.qx.orbit.bili.data.api.LoginApi
import com.qx.orbit.bili.presentation.viewmodel.HdQrCodeLoginViewModel
import com.qx.orbit.bili.presentation.viewmodel.HdQrStatus
import com.qx.orbit.bili.presentation.viewmodel.LoginState
import com.qx.orbit.bili.presentation.viewmodel.LoginViewModel
import com.qx.orbit.bili.util.SharedPreferencesUtil
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import com.google.gson.Gson
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.viewmodel.HdQrCodeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.api.UserInfoApi
import com.qx.orbit.bili.presentation.theme.BiliPink
import kotlin.time.Duration.Companion.seconds

/**
 * 统一登录页：HD 扫码 | Cookie 导入
 *
 * 横向滑动(无底部指示器),每页保持原本的 BiliPink 居中布局。
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
    val useHdQrCode = remember { SharedPreferencesUtil.getBoolean("use_hd_qr_code", true) }

    /*
    LaunchedEffect(webState) {
        // Nothing here anymore, handled by SuccessContent
    }
    LaunchedEffect(hdState.status) {
        // Nothing here anymore, handled by SuccessContent
    }
     */

    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> {
                    if (useHdQrCode) {
                        HdQrPage(hdState, hdLoginViewModel, onLoginSuccess = {
                            onLoginSuccess()
                            navController.popBackStack()
                        })
                    } else {
                        WebQrPage(webState, webLoginViewModel, onLoginSuccess = {
                            onLoginSuccess()
                            navController.popBackStack()
                        })
                    }
                }
                1 -> CookieImportPage(onLoginSuccess = {
                    onLoginSuccess()
                    navController.popBackStack()
                })
            }
        }
        HorizontalPageIndicator(
            pagerState = pagerState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
        )
    }
}

// ── Web QR ──

@Composable
private fun WebQrPage(state: LoginState, vm: LoginViewModel, onLoginSuccess: () -> Unit) {
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
            when (state) {
                is LoginState.Initial, is LoginState.LoadingQRCode -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在获取二维码", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                is LoginState.QRCodeReady -> {
                    QrContent(state.qrImage, state.message)
                }
                is LoginState.Error -> {
                    ErrorContent(state.error) { vm.startLogin() }
                }
                is LoginState.Success -> {
                    SuccessContent(onDone = onLoginSuccess)
                }
            }
        }
    }
}

// ── HD QR ──

@Composable
private fun HdQrPage(state: HdQrCodeState, vm: HdQrCodeLoginViewModel, onLoginSuccess: () -> Unit) {
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
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在获取二维码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                HdQrStatus.WAITING -> {
                    QrContent(
                        qrImage = state.qrCodeUrl?.let { LoginApi.generateQRCodeBitmap(it, 300) },
                        message = "使用哔哩哔哩 App 扫码",
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
                HdQrStatus.LOGIN_SUCCESS -> SuccessContent(onDone = onLoginSuccess)
            }
        }
    }
}

// ── Cookie 导入 ──

@Composable
private fun CookieImportPage(onLoginSuccess: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val coroutineScope = rememberCoroutineScope()

    val performImport = {
        if (!isChecking) {
            keyboardController?.hide()
            isChecking = true
            coroutineScope.launch {
                try {
                    val data = Gson().fromJson(text, CookieImportData::class.java)
                    requireNotNull(data.cookies) { "缺少 cookies" }
                    data.cookies.forEach { CookieManager.putCookie(it.name, it.value) }
                    val navInfo = UserInfoApi.getNavInfo()
                    if (navInfo != null && navInfo.isLogin) {
                        isSuccess = true
                    } else {
                        CookieManager.clearCookie()
                        error = "Cookie 无效或已过期"
                    }
                } catch (e: Exception) {
                    error = e.message ?: "解析失败"
                } finally {
                    isChecking = false
                }
            }
        }
    }

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
            if (isSuccess) {
                SuccessContent(onDone = { onLoginSuccess() })
            } else {
                ListHeader() {
                    Text(text = "Cookie 导入", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                if (error != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(error!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = {
                        Text("粘贴 cookie JSON", color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.background,
                        unfocusedBorderColor = MaterialTheme.colorScheme.background
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { performImport() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledIconButton(
                    onClick = { performImport() },
                    modifier = Modifier.size(IconButtonDefaults.DefaultButtonSize),
                    enabled = text.isNotBlank() && error == null && !isChecking,
                    shapes = IconButtonDefaults.animatedShapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isChecking) {
                        CircularProgressIndicator()
                    } else {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "导入")
                    }
                }
            }
        }
    }
}

// ── 共用组件 ──

@Composable
private fun QrContent(qrImage: android.graphics.Bitmap?, message: String) {
    val isScanned = message.contains("已扫码") || message.contains("手机上")

    Text("扫码登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                CircularProgressIndicator(modifier = Modifier.size(24.dp), colors = ProgressIndicatorDefaults.colors(indicatorColor = BiliPink), strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRetry() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.bili_2233_fail),
                contentDescription = "Error",
                modifier = Modifier.fillMaxWidth().offset(y = (-15).dp)
            )
            Text(
                text = "加载失败，点击重试",
                modifier = Modifier.fillMaxWidth().offset(y = (-10).dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SuccessContent(onDone: () -> Unit) {
    var navInfo by remember { mutableStateOf<UserInfoApi.NavInfoData?>(null) }
    var imageLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        navInfo = UserInfoApi.getNavInfo()
    }
    
    LaunchedEffect(navInfo, imageLoaded) {
        if (navInfo != null) {
            if (navInfo!!.face == null || imageLoaded) {
                delay(3.seconds)
                onDone()
            }
        }
    }

    if (navInfo == null) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("正在加载信息", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    } else {
        if (navInfo?.face != null) {
            AsyncImage(
                model = navInfo!!.face,
                contentDescription = "Avatar",
                modifier = Modifier.size(64.dp).clip(CircleShape),
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = true }
            )
        } else {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(BiliPink),
                contentAlignment = Alignment.Center
            ) {}
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (navInfo?.uname != null) {
            Text("欢迎回来，\n${navInfo!!.uname}", color = BiliPink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        } else {
            Text("登录成功", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("将自动跳转主页", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class CookieImportData(
    val cookies: List<CookieImportItem>? = null,
)
private data class CookieImportItem(
    val name: String = "",
    val value: String = "",
)
