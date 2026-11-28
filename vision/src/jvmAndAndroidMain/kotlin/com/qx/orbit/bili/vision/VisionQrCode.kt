package com.qx.orbit.bili.vision

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.floor

internal fun generateVisionQrCode(content: String): BitMatrix? {
    if (content.isBlank()) return null
    return runCatching {
        QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "utf-8"),
        )
    }.getOrNull()
}

@Composable
internal fun VisionQrCode(matrix: BitMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "登录二维码" }) {
        val moduleSize = floor(size.minDimension / matrix.width)
        val left = floor((size.width - matrix.width * moduleSize) / 2)
        val top = floor((size.height - matrix.height * moduleSize) / 2)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(
                    Color.Black, Offset(left + x * moduleSize, top + y * moduleSize), Size(moduleSize, moduleSize),
                )
            }
        }
    }
}
