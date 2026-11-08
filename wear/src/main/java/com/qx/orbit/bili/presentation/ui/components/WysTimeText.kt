package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.curvedText
import com.qx.orbit.bili.util.SharedPreferencesUtil
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar.getInstance
import java.util.Locale.getDefault
import kotlin.time.Duration.Companion.seconds
import com.qx.orbit.bili.presentation.theme.LocalScreenRound

@Composable
fun WysTimeText() {
    val isRound = LocalScreenRound.current
    var isImmersive by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("ui_immersive_time", true)) }
    var timeString by remember { 
        mutableStateOf(SimpleDateFormat("HH:mm", getDefault()).format(getInstance().time)) 
    }
    isImmersive = SharedPreferencesUtil.getBoolean("ui_immersive_time", true)
    LaunchedEffect(Unit) {
        val dateFormat = SimpleDateFormat("HH:mm", getDefault())
        while (true) {
            timeString = dateFormat.format(getInstance().time)
            delay(1.seconds)
        }
    }

    if (isRound) {
        if (isImmersive) {
            val textColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            val fontSize = MaterialTheme.typography.labelMedium.fontSize
            Box(modifier = Modifier.fillMaxSize()) {
                CurvedLayout(anchor = 270f) {
                    curvedText(
                        text = timeString,
                        style = CurvedTextStyle(
                            fontSize = fontSize,
                            color = textColor,
                            fontWeight = Bold
                        )
                    )
                }
            }
        } else {
            TimeText()
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = timeString,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = Bold),
                modifier = Modifier.background(Color.Transparent)
            )
        }
    }
}
