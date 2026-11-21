package com.qx.orbit.bili.core.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.ZoneId
import okhttp3.OkHttpClient

class BilibiliArticleGateway(private val client: OkHttpClient = OkHttpClient()) : ArticleGateway {
    override suspend fun loadArticle(cv: Long, cookie: String): CoreArticle {
        require(cv > 0)
        val data = client.getBilibiliJson("https://api.bilibili.com/x/article/view?id=$cv", cookie).requireBilibiliData()
        val author = data.objectValue("author")
        val stats = data.objectValue("stats")
        val paragraphs = data.objectValue("opus")?.objectValue("content")?.arrayValue("paragraphs")
        val blocks = if (paragraphs != null && paragraphs.size() > 0) parseParagraphs(paragraphs) else parseArticleHtmlToBlocks(data.text("content"))
        return CoreArticle(
            id = cv.toString(),
            title = data.text("title"),
            author = author?.text("name").orEmpty(),
            authorMid = author?.longValue("mid") ?: 0L,
            avatar = normalizeUrl(author?.text("face")),
            published = formatEpoch(data.longValue("ctime")),
            banner = normalizeUrl(data.text("banner_url")),
            stats = CoreArticleStats(
                view = stats?.longValue("view") ?: 0L,
                like = stats?.longValue("like") ?: 0L,
                reply = stats?.longValue("reply") ?: 0L,
                coin = stats?.longValue("coin") ?: 0L,
                favorite = stats?.longValue("favorite") ?: 0L,
            ),
            blocks = blocks,
        )
    }

    override suspend fun loadOpus(id: String, cookie: String): CoreArticle {
        require(id.isNotBlank())
        val html = client.getBilibiliHtml("https://www.bilibili.com/opus/$id", cookie)
        val root = extractInitialState(html) ?: error("图文页面解析失败，请重试")
        val item = findOpusItem(root, 0)
        val modules = item?.objectValue("modules")
        val author = modules?.objectValue("module_author")
        val stat = modules?.objectValue("module_stat")
        val paragraphs = modules?.objectValue("module_content")?.arrayValue("paragraphs")
            ?: root.objectValue("opus")?.objectValue("content")?.arrayValue("paragraphs")
        val title = root.objectValue("opus")?.text("title").orEmpty()
            .ifBlank { modules?.objectValue("module_dynamic")?.objectValue("topic")?.text("name").orEmpty() }
            .ifBlank { modules?.objectValue("module_title")?.text("text").orEmpty() }
        return CoreArticle(
            id = id,
            title = title,
            author = author?.text("name").orEmpty(),
            authorMid = author?.longValue("mid") ?: 0L,
            avatar = normalizeUrl(author?.text("face")),
            published = formatEpoch(author?.longValue("pub_ts") ?: 0L),
            banner = "",
            stats = CoreArticleStats(
                like = stat?.objectValue("like")?.longValue("count") ?: 0L,
                reply = stat?.objectValue("comment")?.longValue("count") ?: 0L,
                favorite = stat?.objectValue("favorite")?.longValue("count") ?: 0L,
            ),
            blocks = if (paragraphs != null) parseParagraphs(paragraphs) else emptyList(),
        )
    }

    private fun parseParagraphs(paragraphs: JsonArray): List<CoreArticleBlock> {
        val blocks = mutableListOf<CoreArticleBlock>()
        paragraphs.forEach { element ->
            val obj = element.asJsonObjectOrNull() ?: return@forEach
            when (obj.intValue("para_type")) {
                2 -> obj.objectValue("pic")?.arrayValue("pics")?.forEach { pic ->
                    val url = normalizeUrl(pic.asJsonObjectOrNull()?.text("url"))
                    if (url.isNotBlank()) blocks.add(CoreArticleBlock.Image(url))
                }
                3 -> blocks.add(CoreArticleBlock.Divider)
                1, 4, 8 -> {
                    val text = obj.objectValue("text")?.arrayValue("nodes")?.joinToString("") { node ->
                        val nodeObj = node.asJsonObjectOrNull() ?: return@joinToString ""
                        nodeObj.objectValue("word")?.text("words").orEmpty().ifBlank { nodeObj.objectValue("rich")?.text("text").orEmpty() }
                    }.orEmpty().trim()
                    if (text.isNotBlank()) {
                        val kind = when (obj.intValue("para_type")) {
                            8 -> CoreArticleTextKind.HEADING
                            4 -> CoreArticleTextKind.QUOTE
                            else -> CoreArticleTextKind.PARAGRAPH
                        }
                        blocks.add(CoreArticleBlock.Text(text, kind))
                    }
                }
            }
        }
        return blocks
    }

    private fun parseArticleHtmlToBlocks(html: String): List<CoreArticleBlock> {
        if (html.isBlank()) return emptyList()
        val blocks = mutableListOf<CoreArticleBlock>()
        val imgPattern = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""")
        val headingPattern = Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.DOT_MATCHES_ALL)
        val quotePattern = Regex("""<blockquote[^>]*>(.*?)</blockquote>""", RegexOption.DOT_MATCHES_ALL)
        val dividerPattern = Regex("""<hr[^>]*>""")
        val tagStripper = Regex("""<[^>]+>""")
        val entities = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&nbsp;" to " ")
        fun clean(s: String): String {
            var r = tagStripper.replace(s, "")
            entities.forEach { (entity, char) -> r = r.replace(entity, char) }
            return r.replace(Regex("""\n{3,}"""), "\n\n").trim()
        }
        fun addText(s: String, kind: CoreArticleTextKind = CoreArticleTextKind.PARAGRAPH) {
            val text = clean(s)
            if (text.isNotBlank()) blocks.add(CoreArticleBlock.Text(text, kind))
        }
        var remaining = html
        while (remaining.isNotEmpty()) {
            val earliest = listOfNotNull(
                imgPattern.find(remaining)?.let { it to "img" },
                headingPattern.find(remaining)?.let { it to "heading" },
                quotePattern.find(remaining)?.let { it to "quote" },
                dividerPattern.find(remaining)?.let { it to "hr" },
            ).minByOrNull { it.first.range.first }
            if (earliest == null) {
                addText(remaining)
                break
            }
            val (match, type) = earliest
            addText(remaining.substring(0, match.range.first))
            when (type) {
                "img" -> normalizeUrl(match.groupValues[1]).takeIf { it.isNotBlank() }?.let { blocks.add(CoreArticleBlock.Image(it)) }
                "heading" -> addText(match.groupValues[1], CoreArticleTextKind.HEADING)
                "quote" -> addText(match.groupValues[1], CoreArticleTextKind.QUOTE)
                "hr" -> blocks.add(CoreArticleBlock.Divider)
            }
            remaining = remaining.substring(match.range.last + 1)
        }
        return blocks
    }

    private fun extractInitialState(html: String): JsonObject? {
        val marker = "window.__INITIAL_STATE__="
        val start = html.indexOf(marker)
        if (start < 0) return null
        val jsonStart = start + marker.length
        val end = html.indexOf(";(function()", jsonStart).takeIf { it > jsonStart } ?: html.length
        return runCatching { JsonParser.parseString(html.substring(jsonStart, end)).asJsonObject }.getOrNull()
    }

    private fun findOpusItem(element: com.google.gson.JsonElement?, depth: Int): JsonObject? {
        if (depth > 5 || element == null) return null
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.get("modules")?.isJsonObject == true && obj.has("id_str")) return obj
            obj.entrySet().forEach { (_, value) -> findOpusItem(value, depth + 1)?.let { return it } }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { findOpusItem(it, depth + 1)?.let { return it } }
        }
        return null
    }

    private fun normalizeUrl(value: String?): String = when {
        value.isNullOrBlank() -> ""
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        else -> value
    }

    private fun formatEpoch(seconds: Long): String =
        if (seconds <= 0) "" else Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
