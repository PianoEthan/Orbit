package com.qx.orbit.bili.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.wear.compose.material3.MaterialTheme

/**
 * Parses a string containing <em class="keyword">...</em> tags
 * and returns an AnnotatedString with keywords highlighted in primary color.
 */
@Composable
fun parseHighlightedTitle(raw: String, highlightColor: Color = MaterialTheme.colorScheme.primary): AnnotatedString {
    return buildAnnotatedString {
        var remaining = raw
        val emOpenRegex = Regex("""<em[^>]*>""")
        val emClose = "</em>"

        while (remaining.isNotEmpty()) {
            val matchResult = emOpenRegex.find(remaining)
            if (matchResult == null) {
                append(remaining)
                break
            }

            // Append text before <em>
            val beforeEm = remaining.substring(0, matchResult.range.first)
            append(beforeEm)

            // Find end of opening tag
            val afterOpenTag = remaining.substring(matchResult.range.last + 1)
            val closeIdx = afterOpenTag.indexOf(emClose)
            if (closeIdx < 0) {
                // No closing tag, append rest as-is
                append(remaining.substring(matchResult.range.first))
                break
            }

            // Keyword text between <em> and </em>
            val keyword = afterOpenTag.substring(0, closeIdx)
            withStyle(SpanStyle(color = highlightColor)) {
                append(keyword)
            }

            remaining = afterOpenTag.substring(closeIdx + emClose.length)
        }
    }
}
