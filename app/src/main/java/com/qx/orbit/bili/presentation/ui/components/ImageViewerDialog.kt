package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.HorizontalPageIndicator
import coil.compose.AsyncImage

@Composable
fun ImageViewerDialog(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (imageUrls.isEmpty()) return

    Dialog(
        visible = true,
        onDismissRequest = onDismiss
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { imageUrls.size })
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize().clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    var dialogUrl = imageUrls[page]
                    if (!dialogUrl.contains("@")) {
                        dialogUrl = "$dialogUrl@1024w.webp"
                    }
                    AsyncImage(
                        model = dialogUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (imageUrls.size > 1) {
                HorizontalPageIndicator(
                    pagerState = pagerState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}