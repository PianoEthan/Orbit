package com.qx.orbit.bili.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ScreenRotationAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.R
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.player.PLAYER_ACTION_DANMAKU
import com.qx.orbit.bili.presentation.player.PLAYER_ACTION_ROTATE
import com.qx.orbit.bili.presentation.player.PLAYER_ACTION_SCALE
import com.qx.orbit.bili.presentation.player.PLAYER_ACTION_SPEED
import com.qx.orbit.bili.presentation.player.PLAYER_ACTION_SUBTITLE
import com.qx.orbit.bili.presentation.player.PLAYER_ACTION_VOLUME
import com.qx.orbit.bili.presentation.player.PLAYER_LEFT_BOTTOM_KEY
import com.qx.orbit.bili.presentation.player.PLAYER_LEFT_TOP_KEY
import com.qx.orbit.bili.presentation.player.PLAYER_RIGHT_BOTTOM_KEY
import com.qx.orbit.bili.presentation.player.PLAYER_RIGHT_TOP_KEY
import com.qx.orbit.bili.presentation.player.loadPlayerControlLayout

@Composable
fun PlayerCustomizationScreen(
    onBack: () -> Unit
) {
    val initialLayout = remember { loadPlayerControlLayout() }
    var leftTopBtnAction by remember { mutableIntStateOf(initialLayout.leftTop) }
    var leftBottomBtnAction by remember { mutableIntStateOf(initialLayout.leftBottom) }
    var rightTopBtnAction by remember { mutableIntStateOf(initialLayout.rightTop) }
    var rightBottomBtnAction by remember { mutableIntStateOf(initialLayout.rightBottom) }
    
    var showActionDialog by remember { mutableStateOf(false) }
    var configuringSide by remember { mutableIntStateOf(0) } // 0: LT, 1: LB, 2: RT, 3: RB

    val actionNames = listOf("无操作", "弹幕开关", "倍速播放", "音量调节", "字幕设置", "旋转屏幕", "三段缩放")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Mock Player UI
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "正在播放：自定义预览",
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                    .clickable { onBack() }
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side
                Column(
                    modifier = Modifier.offset(x = (-16).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { configuringSide = 0; showActionDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        PlayerActionIcon(action = leftTopBtnAction)
                        if (leftTopBtnAction == 0) Text(text = "左上", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                    }
                    IconButton(
                        onClick = { configuringSide = 1; showActionDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        PlayerActionIcon(action = leftBottomBtnAction)
                        if (leftBottomBtnAction == 0) Text(text = "左下", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                    }
                }
                
                // Play Button (Center)
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(42.dp)
                )
                
                // Right Side
                Column(
                    modifier = Modifier.offset(x = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { configuringSide = 2; showActionDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        PlayerActionIcon(action = rightTopBtnAction)
                        if (rightTopBtnAction == 0) Text(text = "右上", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                    }
                    IconButton(
                        onClick = { configuringSide = 3; showActionDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        PlayerActionIcon(action = rightBottomBtnAction)
                        if (rightBottomBtnAction == 0) Text(text = "右下", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                    }
                }
            }

            // Mock Progress Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }
                Text(
                    text = "点击两侧按钮配置",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
        
        // Action Selection Dialog
        Dialog(
            showDialog = showActionDialog,
            onDismissRequest = { showActionDialog = false }
        ) {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current

            TransformingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    ListHeader{
                        val title = when(configuringSide) {
                            0 -> "设置左上按钮"
                            1 -> "设置左下按钮"
                            2 -> "设置右上按钮"
                            else -> "设置右下按钮"
                        }
                        Text(text = title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                itemsIndexed(actionNames) { index, name ->
                    val isSelected = index == when(configuringSide) {
                        0 -> leftTopBtnAction
                        1 -> leftBottomBtnAction
                        2 -> rightTopBtnAction
                        else -> rightBottomBtnAction
                    }
                    val isDisabled = !isSelected && index != 0 && (
                        index == leftTopBtnAction || 
                        index == leftBottomBtnAction || 
                        index == rightTopBtnAction || 
                        index == rightBottomBtnAction
                    )
                    
                    Button(
                        onClick = {
                            if (!isDisabled) {
                                when(configuringSide) {
                                    0 -> {
                                        leftTopBtnAction = index
                                        SharedPreferencesUtil.putInt(PLAYER_LEFT_TOP_KEY, index)
                                    }
                                    1 -> {
                                        leftBottomBtnAction = index
                                        SharedPreferencesUtil.putInt(PLAYER_LEFT_BOTTOM_KEY, index)
                                    }
                                    2 -> {
                                        rightTopBtnAction = index
                                        SharedPreferencesUtil.putInt(PLAYER_RIGHT_TOP_KEY, index)
                                    }
                                    3 -> {
                                        rightBottomBtnAction = index
                                        SharedPreferencesUtil.putInt(PLAYER_RIGHT_BOTTOM_KEY, index)
                                    }
                                }
                                showActionDialog = false
                            }
                        },
                        colors = if (isSelected) androidx.wear.compose.material3.ButtonDefaults.buttonColors() else androidx.wear.compose.material3.ButtonDefaults.filledTonalButtonColors(),
                        icon = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            } else if (isDisabled) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        enabled = !isDisabled
                    ) {
                        Text(text = name)
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun PlayerActionIcon(action: Int) {
    when (action) {
        PLAYER_ACTION_DANMAKU -> {
            Icon(
                painter = painterResource(R.drawable.ic_danmaku_inline_switch_v2_on),
                contentDescription = "Danmaku",
                tint = Color.Unspecified,
                modifier = Modifier.size(36.dp)
            )
        }
        PLAYER_ACTION_SPEED -> {
            Icon(
                painter = painterResource(R.drawable.speed_1x),
                contentDescription = "Playback Speed",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(28.dp)
            )
        }
        PLAYER_ACTION_VOLUME -> {
            Icon(
                imageVector = Icons.AutoMirrored.Default.VolumeUp,
                contentDescription = "Volume",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
        }
        PLAYER_ACTION_SUBTITLE -> {
            Icon(
                painter = painterResource(R.drawable.ic_subtitle_setting),
                contentDescription = "Subtitle",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
        }
        PLAYER_ACTION_ROTATE -> {
            Icon(
                imageVector = Icons.Filled.ScreenRotation,
                contentDescription = "Rotate Screen",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp)
            )
        }
        PLAYER_ACTION_SCALE -> {
            Icon(
                imageVector = Icons.Filled.Fullscreen,
                contentDescription = "Three-stage Scale",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }
        else -> {
            // Nothing for 0
        }
    }
}
