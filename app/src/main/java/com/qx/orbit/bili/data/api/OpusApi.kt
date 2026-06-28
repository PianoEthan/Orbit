package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object OpusApi {

    suspend fun getOpus(id: Long): Opus? = withContext(Dispatchers.IO) {
        val url = "https://www.bilibili.com/opus/$id"
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val html = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        if (html.isEmpty()) return@withContext null
        try {
            val marker = "window.__INITIAL_STATE__="
            val startIdx = html.indexOf(marker)
            if (startIdx < 0) return@withContext null
            val jsonStart = startIdx + marker.length
            val endIdx = html.indexOf(";(function()", jsonStart)
            if (endIdx < 0) return@withContext null
            val jsonStr = html.substring(jsonStart, endIdx)
            val jsonObj = JSONObject(jsonStr)
            val detailObj = jsonObj.optJSONObject("detail") ?: jsonObj
            val item = detailObj.optJSONObject("item") ?: detailObj
            parseOpusFromHtml(item, id)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseOpusFromHtml(item: JSONObject, id: Long): Opus {
        val dynId = item.optLong("id_str", id)
        val type = item.optInt("type", Opus.TYPE_DYNAMIC)
        
        var authorModule = JSONObject()
        var dynamicModule = JSONObject()
        var statModule = JSONObject()

        val modulesObj = item.optJSONObject("modules")
        if (modulesObj != null) {
            authorModule = modulesObj.optJSONObject("module_author") ?: JSONObject()
            dynamicModule = modulesObj.optJSONObject("module_dynamic") ?: JSONObject()
            statModule = modulesObj.optJSONObject("module_stat") ?: JSONObject()
        } else {
            val modulesArr = item.optJSONArray("modules")
            if (modulesArr != null) {
                for (i in 0 until modulesArr.length()) {
                    val m = modulesArr.optJSONObject(i) ?: continue
                    when (m.optString("module_type")) {
                        "MODULE_TYPE_AUTHOR", "module_author" -> authorModule = m.optJSONObject("module_author") ?: m
                        "MODULE_TYPE_DYNAMIC", "module_dynamic" -> dynamicModule = m.optJSONObject("module_dynamic") ?: m
                        "MODULE_TYPE_STAT", "module_stat" -> statModule = m.optJSONObject("module_stat") ?: m
                    }
                }
            }
        }

        val name = authorModule.optString("name", "")
        val face = authorModule.optString("face", "")
        val mid = authorModule.optLong("mid", 0)
        val pubTs = authorModule.optLong("pub_ts", 0)
        val pubTime = if (pubTs > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(pubTs * 1000)
        } else ""

        val majorObj = dynamicModule.optJSONObject("major")
        val majorType = majorObj?.optString("type", "") ?: ""
        val desc = dynamicModule.optJSONObject("desc")
        val content = desc?.optString("text", "") ?: ""

        val topic = dynamicModule.optJSONObject("topic")
        val title = topic?.optString("name", "") ?: ""

        val topImages = mutableListOf<String>()
        if (majorType == "MAJOR_TYPE_DRAW") {
            val draw = majorObj?.optJSONObject("draw")
            val drawItems = draw?.optJSONArray("items")
            if (drawItems != null) {
                for (i in 0 until drawItems.length()) {
                    val drawItem = drawItems.optJSONObject(i) ?: continue
                    val src = drawItem.optString("src", "")
                    if (src.isNotEmpty()) topImages.add(src)
                }
            }
        }

        val commentStat = statModule.optJSONObject("comment") ?: JSONObject()
        val likeStat = statModule.optJSONObject("like") ?: JSONObject()
        val forwardStat = statModule.optJSONObject("forward") ?: JSONObject()

        val stats = Stats(
            reply = commentStat.optInt("count", 0),
            like = likeStat.optInt("count", 0),
            share = forwardStat.optInt("count", 0),
            liked = likeStat.optBoolean("status", false)
        )

        val basic = item.optJSONObject("basic")
        var commentId = basic?.optString("comment_id_str")?.toLongOrNull() ?: 0L
        var commentType = basic?.optInt("comment_type", 0) ?: 0
        var cover = ""
        when (majorType) {
            "MAJOR_TYPE_ARCHIVE" -> {
                val archive = majorObj?.optJSONObject("archive")
                if (commentId == 0L) commentId = archive?.optLong("aid", 0) ?: 0
                if (commentType == 0) commentType = 1
                cover = archive?.optString("cover", "") ?: ""
            }
            "MAJOR_TYPE_ARTICLE" -> {
                val article = majorObj?.optJSONObject("article")
                if (commentId == 0L) commentId = article?.optLong("id", 0) ?: 0
                if (commentType == 0) commentType = 12
                cover = article?.optString("image_urls")?.let {
                    try {
                        val arr = org.json.JSONArray(it)
                        if (arr.length() > 0) arr.optString(0) else ""
                    } catch (_: Exception) { "" }
                } ?: article?.optString("banner_url", "") ?: ""
            }
            else -> {
                if (commentId == 0L) commentId = dynId
                if (commentType == 0) commentType = 17
            }
        }
        
        var paragraphs: Array<OpusParagraph>? = null
        if (dynamicModule.has("modules")) {
            val dynModules = dynamicModule.optJSONArray("modules")
            // Wait, in Opus format, the top level 'modules' array has 'module_type'.
        }
        // Actually, the BiliClient OpusApi says 'modules' is an array at the root of 'detail', not inside 'module_dynamic'.
        // Let's re-parse properly from detailObj
        
        val parsedParagraphs = mutableListOf<OpusParagraph>()
        val topLevelModules = item.optJSONArray("modules")
        if (topLevelModules != null) {
            for (i in 0 until topLevelModules.length()) {
                val module = topLevelModules.optJSONObject(i) ?: continue
                when (module.optString("module_type")) {
                    "MODULE_TYPE_CONTENT" -> {
                        val moduleContent = module.optJSONObject("module_content")
                        val paras = moduleContent?.optJSONArray("paragraphs")
                        if (paras != null) {
                            parsedParagraphs.addAll(parseParagraphsArray(paras))
                        }
                    }
                }
            }
        }
        
        if (parsedParagraphs.isEmpty()) {
            val contentParas = parseParagraphs(content)
            if (contentParas != null) parsedParagraphs.addAll(contentParas)
        }
        paragraphs = parsedParagraphs.toTypedArray()

        return Opus(
            id = dynId,
            type = type,
            commentId = commentId,
            commentType = commentType,
            title = title,
            cover = cover,
            content = content,
            pubTime = pubTime,
            upInfo = UserInfo(mid = mid, name = name, avatar = face),
            stats = stats,
            topImages = topImages,
            paragraphs = paragraphs,
            parsedId = id
        )
    }

    private fun parseParagraphs(content: String): Array<OpusParagraph>? {
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

    private fun parseParagraphsArray(paras: org.json.JSONArray): List<OpusParagraph> {
        val list = mutableListOf<OpusParagraph>()
        for (i in 0 until paras.length()) {
            val p = paras.optJSONObject(i) ?: continue
            val type = p.optInt("para_type")
            val align = p.optInt("align", 0)
            
            when (type) {
                OpusParagraph.TYPE_TEXT, OpusParagraph.TYPE_HEADING -> {
                    val textObj = p.optJSONObject("text")
                    val nodes = parseTextNodes(textObj?.optJSONArray("nodes"))
                    if (nodes.isNotEmpty()) {
                        list.add(OpusParagraph(align = align, type = type, textNodes = nodes))
                    }
                }
                OpusParagraph.TYPE_PIC -> {
                    val picObj = p.optJSONObject("pic")
                    val picsArr = picObj?.optJSONArray("pics")
                    val urls = mutableListOf<String>()
                    if (picsArr != null) {
                        for (j in 0 until picsArr.length()) {
                            val url = picsArr.optJSONObject(j)?.optString("url", "")
                            if (!url.isNullOrEmpty()) urls.add(url)
                        }
                    }
                    if (urls.isNotEmpty()) {
                        list.add(OpusParagraph(align = align, type = type, pics = urls))
                    }
                }
                OpusParagraph.TYPE_DIVIDER -> {
                    list.add(OpusParagraph(align = align, type = type))
                }
            }
        }
        return list
    }

    private fun parseTextNodes(nodes: org.json.JSONArray?): List<OpusTextNode> {
        if (nodes == null) return emptyList()
        val list = mutableListOf<OpusTextNode>()
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            when (node.optString("type")) {
                "TEXT_NODE_TYPE_WORD" -> {
                    val word = node.optJSONObject("word") ?: continue
                    val text = word.optString("words", "")
                    val style = word.optJSONObject("style")
                    val bold = style?.optBoolean("bold", false) ?: false
                    val italic = style?.optBoolean("italic", false) ?: false
                    val fontSize = word.optInt("font_size", 17)
                    val color = word.optString("color", "")
                    list.add(OpusTextNode(text = text, bold = bold, italic = italic, fontSize = fontSize, color = color))
                }
                "TEXT_NODE_TYPE_RICH" -> {
                    val rich = node.optJSONObject("rich") ?: continue
                    val text = rich.optString("text", "")
                    when (rich.optString("type")) {
                        "RICH_TEXT_NODE_TYPE_EMOJI" -> {
                            val emoji = rich.optJSONObject("emoji")
                            val iconUrl = emoji?.optString("icon_url", "")
                            val size = emoji?.optInt("size", 1) ?: 1
                            list.add(OpusTextNode(text = text, emoteUrl = iconUrl, emoteSize = size))
                        }
                        "RICH_TEXT_NODE_TYPE_RICH", "RICH_TEXT_NODE_TYPE_TOPIC", "RICH_TEXT_NODE_TYPE_AT" -> {
                            val jumpUrl = rich.optString("jump_url", "")
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

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
}
