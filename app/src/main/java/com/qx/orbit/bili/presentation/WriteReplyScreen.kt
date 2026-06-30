package com.qx.orbit.bili.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text as Material3Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import com.qx.orbit.bili.data.api.EmoteApi
import kotlinx.coroutines.delay
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalView
import android.view.inputmethod.InputMethodManager
import kotlin.math.max

@Composable
fun WriteReplyScreen(
    visible: Boolean,
    targetName: String?,
    emotes: List<EmoteApi.EmotePackage>?,
    isLive: Boolean = false,
    onSend: (String) -> Unit,
    onSendEmote: (String) -> Unit = {},
    onClose: () -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var isSending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    // 用于持有 Dialog 内部 View 的引用，以便通过正确的 windowToken 关闭键盘
    var dialogView by remember { mutableStateOf<android.view.View?>(null) }

    fun hideKeyboard() {
        focusManager.clearFocus()
        dialogView?.let { v ->
            val imm = v.context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            isSending = false
            text = TextFieldValue("")
        }
    }

    Dialog(
        visible = visible,
        onDismissRequest = {
            if (isFocused) {
                hideKeyboard()
            } else {
                onClose()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val listState = rememberTransformingLazyColumnState()
        val pagerState = rememberPagerState(pageCount = { emotes?.size ?: 0 })
        val focusRequester = remember { FocusRequester() }

        // 捕获 Dialog 自己的 View，用于通过正确的 windowToken 关闭软键盘
        val currentDialogView = LocalView.current
        SideEffect { dialogView = currentDialogView }

        // Request focus after dialog is fully rendered
        LaunchedEffect(Unit) {
            delay(300)
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }

        // Preload emote images after dialog animation completes
        LaunchedEffect(emotes) {
            delay(400)
            val imageLoader = context.imageLoader
            emotes?.forEach { pkg ->
                if (pkg.type != 4) {
                    pkg.emotes.forEach { emote ->
                        if (emote.url.isNotBlank()) {
                            val request = ImageRequest.Builder(context)
                                .data(emote.url.replace("http://", "https://"))
                                .build()
                            imageLoader.enqueue(request)
                        }
                    }
                }
            }
        }

        // Re-request focus whenever pager page changes so crown always scrolls the outer list
        LaunchedEffect(pagerState.currentPage) {
            delay(100)
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }

        ScreenScaffold(
            scrollState = listState,
            modifier = Modifier.focusRequester(focusRequester)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TransformingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                ) {
                    item {
                        ListHeader {
                            Text(if (targetName.isNullOrEmpty()) "发布评论" else "回复 @$targetName", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    item {
                        ReplyInputLayout(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = {
                                    Material3Text(
                                        "写下你的评论",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                singleLine = false,
                                maxLines = 5,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.background,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onPreRotaryScrollEvent {
                                        hideKeyboard()
                                        false
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { hideKeyboard() }
                                ),
                                visualTransformation = { annotatedString ->
                                    val expandedText = buildString {
                                        for (char in annotatedString.text) {
                                            val emoteName = EmoteMapper.getNameForChar(char)
                                            if (emoteName != null) {
                                                append(emoteName)
                                            } else {
                                                append(char)
                                            }
                                        }
                                    }

                                    val flatEmotes = emotes?.flatMap { it.emotes }?.associateBy { it.name } ?: emptyMap()
                                    val richText = parseRichTextForInput(expandedText, flatEmotes)

                                    val offsetMapping = object : OffsetMapping {
                                        override fun originalToTransformed(offset: Int): Int {
                                            var visualOffset = 0
                                            for (i in 0 until offset.coerceAtMost(annotatedString.text.length)) {
                                                val char = annotatedString.text[i]
                                                val emoteName = EmoteMapper.getNameForChar(char)
                                                if (emoteName != null) {
                                                    visualOffset += emoteName.length
                                                } else {
                                                    visualOffset += 1
                                                }
                                            }
                                            return visualOffset
                                        }

                                        override fun transformedToOriginal(offset: Int): Int {
                                            var visualOffset = 0
                                            var originalOffset = 0
                                            val textLen = annotatedString.text.length
                                            while (originalOffset < textLen) {
                                                if (visualOffset >= offset) break
                                                val char = annotatedString.text[originalOffset]
                                                val emoteName = EmoteMapper.getNameForChar(char)
                                                val len = emoteName?.length ?: 1
                                                if (visualOffset + len > offset) {
                                                    return if (offset - visualOffset > len / 2) originalOffset + 1 else originalOffset
                                                }
                                                visualOffset += len
                                                originalOffset++
                                            }
                                            return originalOffset
                                        }
                                    }

                                    TransformedText(richText, offsetMapping)
                                }
                            )
                            FilledIconButton(
                                onClick = {
                                    if (text.text.isNotEmpty()) {
                                        isSending = true
                                        hideKeyboard()
                                        if (isLive) {
                                            val decoded = EmoteMapper.decode(text.text)
                                            val hasPanelEmote = text.text.any { EmoteMapper.getNameForChar(it) != null }
                                            val matchedEmote = if (hasPanelEmote) {
                                                emotes
                                                    ?.flatMap { it.emotes }
                                                    ?.firstOrNull { it.name == decoded && it.emoticonUnique.isNotBlank() }
                                            } else null
                                            if (matchedEmote != null) {
                                                onSendEmote(matchedEmote.emoticonUnique)
                                            } else {
                                                onSend(decoded)
                                            }
                                        } else {
                                            onSend(EmoteMapper.decode(text.text))
                                        }
                                        text = TextFieldValue("")
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                shapes = IconButtonDefaults.animatedShapes()
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Send",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Emote panel with fade-in transition
                        AnimatedContent(
                            targetState = emotes,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(150))
                            },
                            label = "EmotePanel"
                        ) { currentEmotes ->
                            if (currentEmotes == null) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (currentEmotes.isEmpty()) {
                                Text(
                                    "暂无表情包",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                    Text(
                                        text = currentEmotes[pagerState.currentPage].text,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                    ) { page ->
                                        val pkg = currentEmotes[page]
                                        val chunkSize = if (pkg.type == 4) 1 else 3
                                        val chunkedEmotes = pkg.emotes.chunked(chunkSize)

                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            chunkedEmotes.forEach { rowItems ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceEvenly
                                                ) {
                                                    rowItems.forEach { emote ->
                                                        if (pkg.type == 4 || emote.url.isBlank()) {
                                                            Text(
                                                                text = emote.name,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                modifier = Modifier
                                                                    .height(40.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .clickable {
                                                                        val char = EmoteMapper.getCharForName(emote.name).toString()
                                                                        val currentText = text.text
                                                                        val selection = text.selection
                                                                        val newText = currentText.substring(0, selection.min) + char + currentText.substring(selection.max)
                                                                        val newCursor = selection.min + char.length
                                                                        text = TextFieldValue(newText, TextRange(newCursor))
                                                                    }
                                                                    .padding(4.dp),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        } else {
                                                            AsyncImage(
                                                                model = emote.url.replace("http://", "https://"),
                                                                contentDescription = emote.name,
                                                                contentScale = ContentScale.Fit,
                                                                modifier = Modifier
                                                                    .size(40.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .clickable {
                                                                        val char = EmoteMapper.getCharForName(emote.name).toString()
                                                                        val currentText = text.text
                                                                        val selection = text.selection
                                                                        val newText = currentText.substring(0, selection.min) + char + currentText.substring(selection.max)
                                                                        val newCursor = selection.min + char.length
                                                                        text = TextFieldValue(newText, TextRange(newCursor))
                                                                    }
                                                                    .padding(4.dp)
                                                            )
                                                        }
                                                    }
                                                    // Fill empty spots if row has fewer items than chunkSize
                                                    repeat(chunkSize - rowItems.size) {
                                                        Spacer(modifier = Modifier.size(40.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
                if (emotes != null && emotes.isNotEmpty()) {
                    HorizontalPageIndicator(
                        pagerState = pagerState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

object EmoteMapper {
    private val nameToChar = mutableMapOf<String, Char>()
    private val charToName = mutableMapOf<Char, String>()
    private var nextChar = '\uE000'

    fun getCharForName(name: String): Char {
        return nameToChar.getOrPut(name) {
            val c = nextChar++
            charToName[c] = name
            c
        }
    }

    fun getNameForChar(c: Char): String? = charToName[c]

    fun decode(text: String): String {
        val sb = java.lang.StringBuilder()
        for (c in text) {
            val name = charToName[c]
            if (name != null) {
                sb.append(name)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}

@Composable
fun ReplyInputLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val tfMeasurable = measurables[0]
        val btnMeasurable = measurables[1]

        val btnPlaceable = btnMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val spacing = 8.dp.roundToPx()

        val maxRowWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val rowTfWidth = max(0, maxRowWidth - btnPlaceable.width - spacing)

        val tfHeightAtRowWidth = tfMeasurable.maxIntrinsicHeight(rowTfWidth)
        val singleLineHeightThreshold = 68.dp.roundToPx()

        if (tfHeightAtRowWidth > singleLineHeightThreshold) {
            val tfPlaceable = tfMeasurable.measure(constraints.copy(minWidth = 0))
            val layoutWidth = constraints.maxWidth
            val layoutHeight = tfPlaceable.height + spacing + btnPlaceable.height

            layout(layoutWidth, layoutHeight) {
                tfPlaceable.placeRelative(0, 0)
                btnPlaceable.placeRelative(layoutWidth - btnPlaceable.width, tfPlaceable.height + spacing)
            }
        } else {
            val tfPlaceable = tfMeasurable.measure(constraints.copy(minWidth = 0, maxWidth = rowTfWidth))
            val layoutWidth = constraints.maxWidth
            val layoutHeight = max(tfPlaceable.height, btnPlaceable.height)

            layout(layoutWidth, layoutHeight) {
                tfPlaceable.placeRelative(0, (layoutHeight - tfPlaceable.height) / 2)
                btnPlaceable.placeRelative(layoutWidth - btnPlaceable.width, (layoutHeight - btnPlaceable.height) / 2)
            }
        }
    }
}

fun parseRichTextForInput(
    text: String,
    emotes: Map<String, EmoteApi.Emote>
): androidx.compose.ui.text.AnnotatedString {
    val processedText = text
    val urlPattern = "(https?://[^\\s<>()\\[\\]\"',;!?]+|www\\.[^\\s<>()\\[\\]\"',;!?]+)"
    val videoPattern = "(?i)(bv[A-Za-z0-9]+|av\\d+)"
    val fullPattern = Regex("($urlPattern|$videoPattern)")
    
    return androidx.compose.ui.text.buildAnnotatedString {
        if (emotes.isEmpty() && !processedText.contains(fullPattern)) {
            append(processedText)
            return@buildAnnotatedString
        }
        
        val parts = processedText.split(fullPattern)
        val matches = fullPattern.findAll(processedText).toList()
        
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isNotEmpty()) {
                if (emotes.isNotEmpty()) {
                    val emotePattern = "\\[[^]]+]".toRegex()
                    var lastIdx = 0
                    for (emoteMatch in emotePattern.findAll(part)) {
                        val emoteKey = emoteMatch.value
                        val emote = emotes[emoteKey]
                        if (emote != null) {
                            append(part.substring(lastIdx, emoteMatch.range.first))
                            withStyle(SpanStyle(color = Color(0xFF00A0D8))) {
                                append(emoteKey)
                            }
                        } else {
                            append(part.substring(lastIdx, emoteMatch.range.first))
                            append(emoteKey)
                        }
                        lastIdx = emoteMatch.range.last + 1
                    }
                    append(part.substring(lastIdx))
                } else {
                    append(part)
                }
            }
            
            if (i < matches.size) {
                val match = matches[i].value
                withStyle(SpanStyle(color = Color(0xFF00A0D8))) {
                    append(match)
                }
            }
        }
    }
}
