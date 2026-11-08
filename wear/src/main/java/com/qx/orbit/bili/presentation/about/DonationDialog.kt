package com.qx.orbit.bili.presentation.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.R
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog

@Composable
fun DonationDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    var zoomedImageRes by remember { mutableStateOf<Int?>(null) }
    // Record the last non-null image to keep it visible during dialog close animation
    var lastImageRes by remember { mutableStateOf<Int?>(null) }
    if (zoomedImageRes != null) {
        lastImageRes = zoomedImageRes
    }

    WysAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
        title = "支持开发",
        content = {
                Text(
                    text = "开发 Orbit 投入了大量的时间和精力，如果您觉得好用，可以考虑请作者喝杯咖啡，您的支持是我们前进的最大动力！",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // Images Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "微信",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Image(
                            painter = painterResource(id = R.drawable.qrcode_wx),
                            contentDescription = "WeChat QR",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { zoomedImageRes = R.drawable.qrcode_wx }
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "支付宝",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Image(
                            painter = painterResource(id = R.drawable.qrcode_zfb),
                            contentDescription = "Alipay QR",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { zoomedImageRes = R.drawable.qrcode_zfb }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "点击二维码可放大扫描",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "捐赠请带上您的QQ号",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

        }
    )

    // Zoomed Image Overlay

       Dialog(
            visible = zoomedImageRes != null,
            onDismissRequest = { zoomedImageRes = null }
        ) {
            lastImageRes?.let { resId ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.White)
                        .clickable { zoomedImageRes = null },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = "放大二维码",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(
                            if(resId == R.drawable.qrcode_wx) 5.24.dp else 28.dp
                        )
                    )
                }
            }
        }

}
