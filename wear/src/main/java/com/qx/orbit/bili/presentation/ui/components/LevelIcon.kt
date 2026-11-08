package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.qx.orbit.bili.R

@Composable
fun LevelIcon(
    level: Int,
    isSenior: Boolean,
    modifier: Modifier = Modifier
) {
    val drawableRes = when {
        isSenior -> R.drawable.ic_lv6_senior
        level == 1 -> R.drawable.ic_lv1_v2
        level == 2 -> R.drawable.ic_lv2_v2
        level == 3 -> R.drawable.ic_lv3_v2
        level == 4 -> R.drawable.ic_lv4_v2
        level == 5 -> R.drawable.ic_lv5_v2
        level == 6 -> R.drawable.ic_lv6_v2
        else -> R.drawable.ic_lv0_v2
    }
    
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = "Level $level",
        modifier = modifier.height(20.dp)
    )
}
