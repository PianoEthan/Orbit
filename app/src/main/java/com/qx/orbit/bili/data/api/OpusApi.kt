package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request

object OpusApi {

    internal data class OpusRawItem(
        @SerializedName("id_str") val id_str: String? = null,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("modules") val modules: com.google.gson.JsonElement? = null,
        @SerializedName("basic") val basic: DynamicApi.BasicData? = null
    )

    internal data class OpusModules(
        @SerializedName("module_author") val module_author: DynamicApi.DynamicAuthor? = null,
        @SerializedName("module_dynamic") val module_dynamic: DynamicApi.DynamicContent? = null,
        @SerializedName("module_stat") val module_stat: DynamicApi.StatModule? = null,
        @SerializedName("module_content") val module_content: OpusContentModule? = null
    )

    internal data class OpusContentModule(
        @SerializedName("paragraphs") val paragraphs: List<ParagraphData>? = null
    )

    internal data class ParagraphData(
        @SerializedName("para_type") val para_type: Int = 0,
        @SerializedName("align") val align: Int = 0,
        @SerializedName("text") val text: ParagraphText? = null,
        @SerializedName("pic") val pic: ParagraphPic? = null
    )

    internal data class ParagraphText(
        @SerializedName("nodes") val nodes: List<OpusTextNodeData>? = null
    )

    internal data class ParagraphPic(
        @SerializedName("pics") val pics: List<DynamicApi.PicItem>? = null
    )

    internal data class OpusTextNodeData(
        @SerializedName("type") val type: String? = null,
        @SerializedName("word") val word: WordData? = null,
        @SerializedName("rich") val rich: RichData? = null
    )

    internal data class WordData(
        @SerializedName("words") val words: String? = null,
        @SerializedName("style") val style: StyleData? = null,
        @SerializedName("font_size") val font_size: Int = 17,
        @SerializedName("color") val color: String? = null
    )

    internal data class StyleData(
        @SerializedName("bold") val bold: Boolean = false,
        @SerializedName("italic") val italic: Boolean = false
    )

    internal data class RichData(
        @SerializedName("text") val text: String? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("emoji") val emoji: DynamicApi.EmojiData? = null,
        @SerializedName("jump_url") val jump_url: String? = null
    )

    suspend fun getOpus(id: Long): Opus? = withContext(Dispatchers.IO) {
        android.util.Log.d("BiliApi", "getOpus id=$id")
        fetchOpusFromHtml(id)
    }

    private suspend fun fetchOpusFromHtml(id: Long): Opus? = withContext(Dispatchers.IO) {
        val url = "https://www.bilibili.com/opus/$id"
        android.util.Log.d("BiliApi", "getOpus fetching $url")
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val html = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        if (html.isEmpty()) {
            android.util.Log.w("BiliApi", "getOpus empty HTML")
            return@withContext null
        }
        try {
            val marker = "window.__INITIAL_STATE__="
            val startIdx = html.indexOf(marker)
            if (startIdx < 0) {
                android.util.Log.w("BiliApi", "getOpus marker not found, html=${html.take(200)}")
                return@withContext null
            }
            val jsonStart = startIdx + marker.length
            val endIdx = html.indexOf(";(function()", jsonStart)
            if (endIdx < 0) {
                android.util.Log.w("BiliApi", "getOpus end marker not found")
                return@withContext null
            }
            val jsonStr = html.substring(jsonStart, endIdx)
            android.util.Log.d("BiliApi", "getOpus json=${jsonStr.take(300)}")
            val root = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
            val rawItem = findOpusItem(root)
            if (rawItem == null) {
                android.util.Log.w("BiliApi", "getOpus could not find opus item in JSON")
                return@withContext null
            }
            android.util.Log.d("BiliApi", "getOpus parsed: type=${rawItem.type} modules=${rawItem.modules != null} id=${rawItem.id_str}")
            parseOpusFromHtml(rawItem, id)
        } catch (e: Exception) {
            android.util.Log.e("BiliApi", "getOpus parse error: ${e.message}")
            null
        }
    }

    private fun parseOpusFromHtml(item: OpusRawItem, id: Long): Opus {
        val dynId = item.id_str?.toLongOrNull() ?: id
        val type = item.type

        val modules = resolveModules(item.modules)
        val author = modules?.module_author
        val dynContent = modules?.module_dynamic
        val stat = modules?.module_stat

        val name = author?.name ?: ""
        val face = author?.face ?: ""
        val mid = author?.mid ?: 0
        val pubTs = author?.pub_ts ?: 0
        val pubTime = if (pubTs > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(pubTs * 1000)
        } else ""

        val majorObj = dynContent?.major
        val majorType = majorObj?.type ?: ""
        val content = dynContent?.desc?.text ?: ""

        val title = dynContent?.topic?.name ?: ""

        val topImages = mutableListOf<String>()
        if (majorType == "MAJOR_TYPE_DRAW") {
            majorObj?.draw?.items?.forEach { drawItem ->
                drawItem.src?.fixUrl()?.let { topImages.add(it) }
            }
        }

        val stats = Stats(
            reply = stat?.comment?.count ?: 0,
            like = stat?.like?.count ?: 0,
            share = stat?.forward?.count ?: 0,
            liked = stat?.like?.status ?: false
        )

        val basic = item.basic
        var commentId = basic?.comment_id_str?.toLongOrNull() ?: 0L
        var commentType = basic?.comment_type ?: 0
        var cover = ""
        when (majorType) {
            "MAJOR_TYPE_ARCHIVE" -> {
                if (commentId == 0L) commentId = majorObj?.archive?.aid ?: 0
                if (commentType == 0) commentType = 1
                cover = majorObj?.archive?.cover?.fixUrl() ?: ""
            }
            "MAJOR_TYPE_ARTICLE" -> {
                if (commentId == 0L) commentId = majorObj?.article?.id ?: 0
                if (commentType == 0) commentType = 12
            }
            else -> {
                if (commentId == 0L) commentId = dynId
                if (commentType == 0) commentType = 17
            }
        }

        val parsedParagraphs = mutableListOf<OpusParagraph>()
        val contentModule = modules?.module_content
        if (contentModule?.paragraphs != null) {
            parsedParagraphs.addAll(parseParagraphsArray(contentModule.paragraphs))
        }

        if (parsedParagraphs.isEmpty()) {
            parseContentParagraphs(content)?.let { parsedParagraphs.addAll(it) }
        }
        val paragraphs = parsedParagraphs.toTypedArray()

        val officialVerify = author?.official_verify
        val rawOfficial = officialVerify?.type ?: -1
        val officialType = when (rawOfficial) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        val officialDesc = officialVerify?.desc ?: ""

        val vipObj = author?.vip
        val vipStatus = if (vipObj != null && vipObj.status != 0) vipObj.status else vipObj?.vipStatus ?: 0

        return Opus(
            id = dynId,
            type = type,
            commentId = commentId,
            commentType = commentType,
            title = title,
            cover = cover,
            content = content,
            pubTime = pubTime,
            upInfo = UserInfo(mid = mid, name = name, avatar = face, official = officialType, officialDesc = officialDesc, vip_role = vipStatus),
            stats = stats,
            topImages = topImages,
            paragraphs = paragraphs,
            parsedId = id
        )
    }

    private fun parseContentParagraphs(content: String): Array<OpusParagraph>? {
        if (content.isEmpty()) return null
        val lines = content.split("\n")
        return lines.map { line ->
            OpusParagraph(
                align = 0,
                type = OpusParagraph.TYPE_TEXT,
                textNodes = listOf(OpusTextNode(text = line))
            )
        }.toTypedArray()
    }

    private fun parseParagraphsArray(paras: List<ParagraphData>): List<OpusParagraph> {
        val list = mutableListOf<OpusParagraph>()
        for (p in paras) {
            when (p.para_type) {
                OpusParagraph.TYPE_TEXT, OpusParagraph.TYPE_HEADING -> {
                    val nodes = parseTextNodes(p.text?.nodes)
                    if (nodes.isNotEmpty()) {
                        list.add(OpusParagraph(align = p.align, type = p.para_type, textNodes = nodes))
                    }
                }
                OpusParagraph.TYPE_PIC -> {
                    val urls = p.pic?.pics?.mapNotNull { it.url?.fixUrl()?.takeIf { u -> u.isNotEmpty() } } ?: emptyList()
                    if (urls.isNotEmpty()) {
                        list.add(OpusParagraph(align = p.align, type = p.para_type, pics = urls))
                    }
                }
                OpusParagraph.TYPE_DIVIDER -> {
                    list.add(OpusParagraph(align = p.align, type = p.para_type))
                }
            }
        }
        return list
    }

    private fun parseTextNodes(nodes: List<OpusTextNodeData>?): List<OpusTextNode> {
        if (nodes == null) return emptyList()
        val list = mutableListOf<OpusTextNode>()
        for (node in nodes) {
            when (node.type) {
                "TEXT_NODE_TYPE_WORD" -> {
                    val word = node.word ?: continue
                    list.add(OpusTextNode(
                        text = word.words ?: "",
                        bold = word.style?.bold ?: false,
                        italic = word.style?.italic ?: false,
                        fontSize = word.font_size,
                        color = word.color ?: ""
                    ))
                }
                "TEXT_NODE_TYPE_RICH" -> {
                    val rich = node.rich ?: continue
                    val text = rich.text ?: ""
                    when (rich.type) {
                        "RICH_TEXT_NODE_TYPE_EMOJI" -> {
                            val iconUrl = rich.emoji?.icon_url?.fixUrl() ?: ""
                            val size = rich.emoji?.size ?: 1
                            list.add(OpusTextNode(text = text, emoteUrl = iconUrl, emoteSize = size))
                        }
                        "RICH_TEXT_NODE_TYPE_RICH", "RICH_TEXT_NODE_TYPE_TOPIC", "RICH_TEXT_NODE_TYPE_AT" -> {
                            val jumpUrl = rich.jump_url ?: ""
                            list.add(OpusTextNode(text = text, jumpUrl = jumpUrl, color = "#FB7299"))
                        }
                        else -> {
                            list.add(OpusTextNode(text = text))
                        }
                    }
                }
            }
        }
        return list
    }

    suspend fun likeOpus(dynId: Long, up: Boolean): Int = withContext(Dispatchers.IO) {
        val csrf = CookieManager.getCsrf()
        val jsonStr = "{\"dyn_id_str\":\"$dynId\",\"up\":${if (up) 1 else 2},\"spmid\":\"333.1369.0.0\",\"from_spmid\":\"333.1387.0.0\"}"
        val body = jsonStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/dynamic/feed/dyn/thumb?csrf=$csrf")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val typeToken = object : TypeToken<ApiResponse<*>>() {}.type
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, typeToken)
        resp?.code ?: -1
    }

    private fun resolveModules(el: com.google.gson.JsonElement?): OpusModules? {
        if (el == null || el.isJsonNull) return null
        if (el.isJsonObject) return GsonConfig.gson.fromJson(el, OpusModules::class.java)
        if (!el.isJsonArray) return null
        var author: DynamicApi.DynamicAuthor? = null
        var dynamic: DynamicApi.DynamicContent? = null
        var stat: DynamicApi.StatModule? = null
        var content: OpusContentModule? = null
        for (m in el.asJsonArray) {
            if (!m.isJsonObject) continue
            val obj = m.asJsonObject
            when (obj.get("module_type")?.asString) {
                "MODULE_TYPE_AUTHOR", "module_author" -> author = GsonConfig.gson.fromJson(obj.get("module_author") ?: obj, DynamicApi.DynamicAuthor::class.java)
                "MODULE_TYPE_DYNAMIC", "module_dynamic" -> dynamic = GsonConfig.gson.fromJson(obj.get("module_dynamic") ?: obj, DynamicApi.DynamicContent::class.java)
                "MODULE_TYPE_STAT", "module_stat" -> stat = GsonConfig.gson.fromJson(obj.get("module_stat") ?: obj, DynamicApi.StatModule::class.java)
                "MODULE_TYPE_CONTENT", "module_content" -> content = GsonConfig.gson.fromJson(obj.get("module_content") ?: obj, OpusContentModule::class.java)
            }
        }
        return OpusModules(author, dynamic, stat, content)
    }

    private fun findOpusItem(root: com.google.gson.JsonObject): OpusRawItem? {
        if (root.has("modules") && root.has("id_str")) {
            return GsonConfig.gson.fromJson(root, OpusRawItem::class.java)
        }
        for (key in root.keySet()) {
            val child = root.get(key)
            if (child.isJsonObject) {
                val result = findOpusItem(child.asJsonObject)
                if (result != null) return result
            }
        }
        return null
    }

    private fun String.fixUrl(): String = when {
        startsWith("//") -> "https:$this"
        startsWith("http://") -> replaceFirst("http://", "https://")
        else -> this
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
}
