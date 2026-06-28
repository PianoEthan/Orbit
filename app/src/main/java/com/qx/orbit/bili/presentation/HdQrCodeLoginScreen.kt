package com.qx.orbit.bili.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.data.api.LoginApi
import com.qx.orbit.bili.presentation.viewmodel.HdQrCodeLoginViewModel
import com.qx.orbit.bili.presentation.viewmodel.HdQrStatus

private val BiliPink = Color(0xFFFB7299)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSecondary = Color(0xFFAAAAAA)

/**
 * HD QR 扫码登录页面。
 *
 * 可作为独立页面使用(传入 onLoginSuccess 回调),也可嵌入 LoginScreen 的 HorizontalPager。
 */
@Composable
fun HdQrCodeLoginScreen(
    viewModel: HdQrCodeLoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (state.status == HdQrStatus.REQUESTING) viewModel.requestQrCode()
    }

    LaunchedEffect(state.status) {
        if (state.status == HdQrStatus.LOGIN_SUCCESS) onLoginSuccess()
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
                    val qrUrl = state.qrCodeUrl
                    Text("扫码登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        val bitmap = remember(qrUrl) {
                            qrUrl?.takeIf { it.isNotBlank() }?.let { LoginApi.generateQRCodeBitmap(it, 300) }
                        }
                        if (bitmap != null) {
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BiliPink, strokeWidth = 2.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("使用哔哩哔哩官方 App 扫描", fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
                }
                HdQrStatus.SCANNED -> {
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(BiliPink), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, "Scanned", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("请在手机上确认登录", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BiliPink, textAlign = TextAlign.Center)
                }
                HdQrStatus.EXPIRED, HdQrStatus.ERROR -> {
                    Text(state.error ?: "加载失败", color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.requestQrCode() }, colors = ButtonDefaults.buttonColors(containerColor = BiliPink), modifier = Modifier.height(36.dp).widthIn(min = 80.dp)) {
                        Text("重新获取", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                HdQrStatus.LOGIN_SUCCESS -> {
                    Text("登录成功", color = BiliPink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("正在跳转...", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}