package com.qx.orbit.bili.presentation.util

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import com.qx.orbit.bili.data.model.Emote

private val htmlTagPattern = Regex("<[^>]+>")
private val linkPattern = Regex("https?://[^\\s<>()\\[\\]\"',;!?，。！？、：；“”（）【】《》]+|www\\.[^\\s<>()\\[\\]\"',;!?，。！？、：；“”（）【】《》]+|(?i:bv[A-Za-z0-9]+|av\\d+)")
private val videoPattern = Regex("bv[A-Za-z0-9]+|av\\d+", RegexOption.IGNORE_CASE)
private val tokenPattern = Regex("\\[[^]]+]|@([\\w\\u4e00-\\u9fa5_-]+)|#([^#\\n]+)#")

internal data class ParsedRichText(
    val text: AnnotatedString,
    val inlineEmotes: Map<String, Emote>
)

internal fun buildRichText(
    text: String,
    emotes: Map<String, Emote>,
    members: Map<String, Long> = emptyMap(),
    resolvedB23Links: Map<String, String> = emptyMap(),
    primaryColor: Color = Color(0xFF4FC3F7)
): ParsedRichText {
    var processedText = text
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n")
        .replace(htmlTagPattern, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    for ((link, video) in resolvedB23Links) processedText = processedText.replace(link, video)
    val inlineEmotes = mutableMapOf<String, Emote>()
    val annotated = buildAnnotatedString {
        fun appendTokens(part: String) {
            var lastIndex = 0
            for (match in tokenPattern.findAll(part)) {
                val token = match.value
                val emote = emotes[token].takeIf { token.startsWith("[") }
                val mid = members[match.groupValues[1]].takeIf { token.startsWith("@") }
                val topic = match.groupValues[2].takeIf { token.startsWith("#") }
                if (emote == null && mid == null && topic == null) continue
                append(part.substring(lastIndex, match.range.first))
                if (emote != null) {
                    appendInlineContent(token, token)
                    inlineEmotes[token] = emote
                } else {
                    pushStringAnnotation(if (mid != null) "USER" else "TOPIC", mid?.toString() ?: topic.orEmpty())
                    withStyle(SpanStyle(color = primaryColor)) { append(token) }
                    pop()
                }
                lastIndex = match.range.last + 1
            }
            append(part.substring(lastIndex))
        }
        var lastIndex = 0
        for (match in linkPattern.findAll(processedText)) {
            appendTokens(processedText.substring(lastIndex, match.range.first))
            val link = match.value.trimEnd('.', ',', ';', ':', '!', '?')
            val isVideo = videoPattern.matches(link)
            val annotation = if (isVideo) "video:$link" else "url:${if (link.startsWith("www.")) "https://$link" else link}"
            pushStringAnnotation(if (isVideo) "VIDEO" else "URL", annotation)
            withStyle(SpanStyle(color = Color(0xFF4FC3F7), textDecoration = TextDecoration.Underline)) { append(link) }
            pop()
            append(match.value.substring(link.length))
            lastIndex = match.range.last + 1
        }
        appendTokens(processedText.substring(lastIndex))
    }
    return ParsedRichText(annotated, inlineEmotes)
}

@Composable
fun parseRichText(
    text: String,
    emotes: Map<String, Emote>,
    members: Map<String, Long> = emptyMap(),
    resolvedB23Links: Map<String, String> = emptyMap()
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val primaryColor = MaterialTheme.colorScheme.primary
    val parsed = remember(text, emotes, members, resolvedB23Links, primaryColor) {
        buildRichText(text, emotes, members, resolvedB23Links, primaryColor)
    }
    val inlineContent = remember(parsed.inlineEmotes) {
        parsed.inlineEmotes.mapValues { (_, emote) ->
            val size = (emote.size.coerceAtLeast(1) * 18).sp
            InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage(model = emote.url, contentDescription = emote.name, modifier = Modifier.fillMaxSize())
            }
        }
    }
    return remember(parsed.text, inlineContent) { parsed.text to inlineContent }
}
