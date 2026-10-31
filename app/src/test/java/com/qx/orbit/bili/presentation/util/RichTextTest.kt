package com.qx.orbit.bili.presentation.util

import com.qx.orbit.bili.data.model.Emote
import org.junit.Assert.*
import org.junit.Test

class RichTextTest {
    @Test
    fun htmlAndEntitiesKeepTheirTextAndLineBreaks() {
        val result = buildRichText("<b>正文</b><br/>&lt;内容&gt; &amp; &#39;", emptyMap())
        assertEquals("正文\n<内容> & '", result.text.text)
        assertTrue(result.inlineEmotes.isEmpty())
    }

    @Test
    fun mixedTokensKeepUnknownEmotesAndAnnotateKnownMembersAndTopics() {
        val emote = Emote(name = "[笑]", url = "https://example.com/emote.png")
        val result = buildRichText("[未知] [笑] @用户 #话题#", mapOf("[笑]" to emote), mapOf("用户" to 42L))
        assertEquals("[未知] [笑] @用户 #话题#", result.text.text)
        assertEquals(setOf("[笑]"), result.inlineEmotes.keys)
        assertEquals("42", result.text.getStringAnnotations("USER", 0, result.text.length).single().item)
        assertEquals("话题", result.text.getStringAnnotations("TOPIC", 0, result.text.length).single().item)
    }

    @Test
    fun resolvingShortLinkUpdatesVideoAnnotationAndPreservesPunctuation() {
        val source = "看 https://b23.tv/abc 和 www.example.com."
        val before = buildRichText(source, emptyMap())
        assertEquals(2, before.text.getStringAnnotations("URL", 0, before.text.length).size)
        val after = buildRichText(source, emptyMap(), resolvedB23Links = mapOf("https://b23.tv/abc" to "BV1abc"))
        assertEquals("看 BV1abc 和 www.example.com.", after.text.text)
        assertEquals("video:BV1abc", after.text.getStringAnnotations("VIDEO", 0, after.text.length).single().item)
        assertEquals("url:https://www.example.com", after.text.getStringAnnotations("URL", 0, after.text.length).single().item)
    }
}
