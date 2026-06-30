package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.qx.orbit.bili.data.model.Emote
import com.qx.orbit.bili.util.formatBiliTime

object DynamicApi {

    internal data class DynamicListData(
        @SerializedName("items") val items: List<DynamicRawItem>? = null,
        @SerializedName("has_more") val has_more: Boolean = false,
        @SerializedName("offset") val offset: String? = null
    )

    internal data class DynamicDetailData(
        @SerializedName("item") val item: DynamicRawItem? = null
    )

    internal data class UpdateData(
        @SerializedName("update_num") val update_num: Int = 0
    )

    internal data class PortalData(
        @SerializedName("up_list") val up_list: List<UpInfo>? = null
    )

    internal data class DynamicIdData(
        @SerializedName("dynamic_id") val dynamic_id: Long = 0
    )

    internal data class MentionData(
        @SerializedName("groups") val groups: List<MentionGroup>? = null
    )

    internal data class MentionDataInner(
        @SerializedName("data") val data: MentionData? = null
    )

    internal data class MentionGroup(
        @SerializedName("group_name") val group_name: String? = null,
        @SerializedName("items") val items: List<MentionItem>? = null
    )

    internal data class MentionItem(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("face") val face: String? = null
    )

    data class UpInfo(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("face") val face: String? = null,
        @SerializedName("has_update") val has_update: Boolean = false
    )

    internal data class DynamicRawItem(
        @SerializedName("id_str") val id_str: String? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("modules") val modules: DynamicModules? = null,
        @SerializedName("orig") val orig: DynamicRawItem? = null,
        @SerializedName("basic") val basic: BasicData? = null
    )

    internal data class DynamicModules(
        @SerializedName("module_author") val module_author: DynamicAuthor? = null,
        @SerializedName("module_dynamic") val module_dynamic: DynamicContent? = null,
        @SerializedName("module_stat") val module_stat: StatModule? = null,
        @SerializedName("module_more") val module_more: MoreModule? = null
    )

    internal data class DynamicAuthor(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("face") val face: String? = null,
        @SerializedName("pub_ts") val pub_ts: Long = 0,
        @SerializedName("desc") val desc: String? = null,
        @SerializedName("is_top") val is_top: Boolean = false,
        @SerializedName("official_verify") val official_verify: OfficialVerifyData? = null,
        @SerializedName("vip") val vip: VipStatusData? = null
    )

    internal data class OfficialVerifyData(
        @SerializedName("type") val type: Int = -1,
        @SerializedName("desc") val desc: String? = null
    )

    internal data class VipStatusData(
        @SerializedName("status") val status: Int = 0,
        @SerializedName("vipStatus") val vipStatus: Int = 0
    )

    internal data class DynamicContent(
        @SerializedName("major") val major: DynamicMajor? = null,
        @SerializedName("desc") val desc: DescData? = null,
        @SerializedName("topic") val topic: TopicData? = null
    )

    internal data class DynamicMajor(
        @SerializedName("type") val type: String? = null,
        @SerializedName("archive") val archive: ArchiveMajor? = null,
        @SerializedName("draw") val draw: DrawMajor? = null,
        @SerializedName("opus") val opus: OpusMajor? = null,
        @SerializedName("article") val article: ArticleMajor? = null,
        @SerializedName("common") val common: CommonMajor? = null,
        @SerializedName("live_rcmd") val live_rcmd: LiveRcmdMajor? = null
    )

    internal data class LiveRcmdMajor(
        @SerializedName("content") val content: String? = null
    )

    internal data class ArchiveMajor(
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("bvid") val bvid: String? = null
    )

    internal data class DrawMajor(
        @SerializedName("items") val items: List<DrawItem>? = null,
        @SerializedName("desc") val desc: String? = null
    )

    internal data class DrawItem(
        @SerializedName("src") val src: String? = null
    )

    internal data class OpusMajor(
        @SerializedName("title") val title: String? = null,
        @SerializedName("pics") val pics: List<PicItem>? = null,
        @SerializedName("summary") val summary: OpusSummary? = null
    )

    internal data class OpusSummary(
        @SerializedName("text") val text: String? = null,
        @SerializedName("rich_text_nodes") val rich_text_nodes: List<RichTextNode>? = null
    )

    internal data class PicItem(
        @SerializedName("url") val url: String? = null
    )

    internal data class ArticleMajor(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("title") val title: String? = null
    )

    internal data class CommonMajor(
        @SerializedName("title") val title: String? = null
    )

    internal data class DescData(
        @SerializedName("text") val text: String? = null,
        @SerializedName("rich_text_nodes") val rich_text_nodes: List<RichTextNode>? = null
    )

    internal data class RichTextNode(
        @SerializedName("type") val type: String? = null,
        @SerializedName("text") val text: String? = null,
        @SerializedName("rid") val rid: String? = null,
        @SerializedName("emoji") val emoji: EmojiData? = null
    )

    internal data class EmojiData(
        @SerializedName("icon_url") val icon_url: String? = null,
        @SerializedName("size") val size: Int = 1
    )

    internal data class TopicData(
        @SerializedName("name") val name: String? = null
    )

    internal data class StatModule(
        @SerializedName("comment") val comment: StatItem? = null,
        @SerializedName("like") val like: StatItem? = null,
        @SerializedName("forward") val forward: StatItem? = null
    )

    internal data class StatItem(
        @SerializedName("count") val count: Int = 0,
        @SerializedName("status") val status: Boolean = false
    )

    internal data class MoreModule(
        @SerializedName("dyn_id_str") val dyn_id_str: String? = null
    )

    internal data class BasicData(
        @SerializedName("comment_id_str") val comment_id_str: String? = null,
        @SerializedName("comment_type") val comment_type: Int = 0
    )

    suspend fun publishTextContent(content: String): Long = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("dynamic_id", "0")
            .add("type", "4")
            .add("rid", "0")
            .add("content", content)
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/dynamic/feed/create/dyn")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: return@withContext -1L
        val type = object : TypeToken<ApiResponse<DynamicIdData>>() {}.type
        val resp: ApiResponse<DynamicIdData>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) -1L else resp.data.dynamic_id
    }

    suspend fun likeDynamic(dyid: String, up: Boolean): Int = withContext(Dispatchers.IO) {
        val csrf = CookieManager.getCsrf()
        val jsonStr = "{\"dyn_id_str\":\"$dyid\",\"up\":${if (up) 1 else 2},\"csrf\":\"$csrf\"}"
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

    suspend fun deleteDynamic(dyid: Long): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("dynamic_id", dyid.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/dynamic/feed/dyn/delete")
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

    suspend fun mentionAtFindUser(name: String): Long = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/mention/search?keyword=${java.net.URLEncoder.encode(name, "UTF-8")}"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<MentionDataInner>>() {}.type
        val resp: ApiResponse<MentionDataInner>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext 0L
        resp.data.data?.groups?.firstOrNull()?.items?.firstOrNull()?.mid ?: 0L
    }

    suspend fun getDynamicList(
        offset: String = "",
        mid: Long = 0,
        type: Int = 0
    ): Pair<Long, List<Dynamic>> = withContext(Dispatchers.IO) {
        val features = "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,avatarAutoTheme,sunflowerStyle,eva3CardOpus,eva3CardVideo,eva3CardComment"
        val rawUrl = if (mid > 0) {
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid=$mid&offset=$offset&features=$features"
        } else {
            var params = "offset=$offset&features=$features"
            if (type > 0) params += "&type=$type"
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?$params"
        }
        val url = ConfInfoApi.signWBI(rawUrl)
        val json = httpGet(url)
        val respType = object : TypeToken<ApiResponse<DynamicListData>>() {}.type
        val resp: ApiResponse<DynamicListData>? = GsonConfig.gson.fromJson(json, respType)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext Pair(0L, emptyList())
        val data = resp.data
        val dynamicList = mutableListOf<Dynamic>()
        for (item in data.items ?: emptyList()) {
            try {
                dynamicList.add(analyzeDynamic(item))
            } catch (_: Exception) {
            }
        }
        Pair(if (data.has_more) (data.offset?.toLongOrNull() ?: 0L) else 0L, dynamicList)
    }

    suspend fun getDynamic(id: String): Dynamic? = withContext(Dispatchers.IO) {
        val features = "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,avatarAutoTheme,sunflowerStyle,eva3CardOpus,eva3CardVideo,eva3CardComment"
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id=$id&features=$features"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<DynamicDetailData>>() {}.type
        val resp: ApiResponse<DynamicDetailData>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data?.item == null) return@withContext null
        try {
            analyzeDynamic(resp.data.item)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkDynamicUpdate(type: Int, updateBaseline: String): Int = withContext(Dispatchers.IO) {
        val rawUrl = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all/update?type=$type&update_baseline=$updateBaseline"
        val url = ConfInfoApi.signWBI(rawUrl)
        val json = httpGet(url)
        val respType = object : TypeToken<ApiResponse<UpdateData>>() {}.type
        val resp: ApiResponse<UpdateData>? = GsonConfig.gson.fromJson(json, respType)
        resp?.data?.update_num ?: 0
    }

    suspend fun getRecentUpList(): List<UpInfo> = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/portal"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<PortalData>>() {}.type
        val resp: ApiResponse<PortalData>? = GsonConfig.gson.fromJson(json, type)
        resp?.data?.up_list ?: emptyList()
    }

    suspend fun clearUpUpdate(mid: Long) = withContext(Dispatchers.IO) {
        val csrf = CookieManager.getCsrf()
        val jsonStr = "{\"up_mid\":\"$mid\",\"csrf\":\"$csrf\"}"
        val body = jsonStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/polymer/web-dynamic/v1/up/view")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        try {
            HttpClient.client.newCall(request).execute().use { }
        } catch (_: Exception) {}
    }

    private fun analyzeDynamic(item: DynamicRawItem): Dynamic {
        val dynamicId = item.id_str ?: ""
        val dynType = item.type ?: ""
        val modules = item.modules

        val author = modules?.module_author
        val mid = author?.mid ?: 0
        val name = author?.name ?: ""
        val face = author?.face ?: ""
        val pubTs = author?.pub_ts ?: 0
        val pubTime = if (pubTs > 0) formatBiliTime(pubTs) else ""
        val canDelete = author?.is_top ?: false

        val dynContent = modules?.module_dynamic
        val majorObj = dynContent?.major
        val majorType = majorObj?.type ?: ""
        val topicTitle = dynContent?.topic?.name ?: ""

        var content = dynContent?.desc?.text ?: ""

        var bvid = ""
        var archiveTitle = ""
        var liveState = 0
        val emotes = mutableMapOf<String, Emote>()
        val members = mutableMapOf<String, Long>()
        parseRichTextNodes(dynContent?.desc?.rich_text_nodes, emotes, members)

        var commentId = item.basic?.comment_id_str?.toLongOrNull() ?: 0L
        var commentType = item.basic?.comment_type ?: 0
        val images = mutableListOf<String>()
        var cover = ""

        when (majorType) {
            "MAJOR_TYPE_ARCHIVE" -> {
                val archive = majorObj?.archive
                if (commentId == 0L) commentId = archive?.aid ?: 0
                if (commentType == 0) commentType = 1
                archiveTitle = archive?.title ?: ""
                cover = archive?.cover?.fixUrl() ?: ""
                bvid = archive?.bvid ?: ""
                if (content.isEmpty()) content = archiveTitle
            }
            "MAJOR_TYPE_DRAW" -> {
                if (commentType == 0) commentType = 11
                majorObj?.draw?.items?.forEach { drawItem ->
                    drawItem.src?.fixUrl()?.takeIf { it.isNotEmpty() }?.let { images.add(it) }
                }
                if (content.isEmpty()) majorObj?.draw?.desc?.takeIf { it.isNotEmpty() }?.let { content = it }
            }
            "MAJOR_TYPE_OPUS" -> {
                if (commentType == 0) commentType = 11
                majorObj?.opus?.pics?.forEach { pic ->
                    pic.url?.fixUrl()?.takeIf { it.isNotEmpty() }?.let { images.add(it) }
                }
                val summary = majorObj?.opus?.summary
                parseRichTextNodes(summary?.rich_text_nodes, emotes, members)
                val summaryText = summary?.text ?: ""
                val opusTitle = majorObj?.opus?.title ?: ""
                if (content.isEmpty()) content = summaryText.ifEmpty { opusTitle }
            }
            "MAJOR_TYPE_ARTICLE" -> {
                if (commentId == 0L) commentId = majorObj?.article?.id ?: 0
                if (commentType == 0) commentType = 12
                val title = majorObj?.article?.title ?: ""
                if (content.isEmpty()) content = title
            }
            "MAJOR_TYPE_LIVE_RCMD" -> {
                if (commentType == 0) commentType = 17
                majorObj?.live_rcmd?.content?.let { jsonStr ->
                    try {
                        val obj = JsonParser.parseString(jsonStr).asJsonObject
                        val livePlayInfo = obj.getAsJsonObject("live_play_info")
                        if (livePlayInfo != null) {
                            val title = livePlayInfo.get("title")?.asString ?: ""
                            val cov = livePlayInfo.get("cover")?.asString ?: ""
                            if (content.isEmpty()) content = title
                            cover = cov.fixUrl()
                            val roomId = livePlayInfo.get("room_id")?.asString ?: ""
                            bvid = roomId
                            if (archiveTitle.isEmpty()) archiveTitle = title
                            
                            val status = livePlayInfo.get("live_status")?.asInt
                            if (status != null) {
                                liveState = status
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            "MAJOR_TYPE_COMMON" -> {
                if (commentType == 0) commentType = 17
                if (content.isEmpty()) content = majorObj?.common?.title ?: ""
            }
            else -> {
                if (commentId == 0L) commentId = dynamicId.toLongOrNull() ?: 0L
                if (commentType == 0) commentType = 17
            }
        }

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

        val userInfo = UserInfo(
            mid = mid,
            name = name,
            avatar = face,
            official = officialType,
            officialDesc = officialDesc,
            vip_role = vipStatus
        )

        val stat = modules?.module_stat
        val stats = Stats(
            reply = stat?.comment?.count ?: 0,
            like = stat?.like?.count ?: 0,
            share = stat?.forward?.count ?: 0,
            liked = stat?.like?.status ?: false
        )

        val dynamicForward = item.orig?.let {
            try { analyzeDynamic(it) } catch (_: Exception) { null }
        }

        return Dynamic(
            dynamicId = dynamicId,
            type = dynType,
            comment_id = commentId,
            comment_type = commentType,
            title = topicTitle.ifEmpty { "" },
            userInfo = userInfo,
            content = content,
            pubTime = pubTime,
            stats = stats,
            major_type = majorType,
            major_object = null,
            dynamic_forward = dynamicForward,
            canDelete = canDelete,
            images = images,
            cover = cover,
            bvid = bvid,
            archiveTitle = archiveTitle,
            emotes = emotes,
            members = members,
            liveState = liveState
        )
    }

    private fun parseRichTextNodes(nodes: List<RichTextNode>?, emotes: MutableMap<String, Emote>, members: MutableMap<String, Long>) {
        if (nodes == null) return
        for (node in nodes) {
            val type = node.type ?: ""
            val text = node.text ?: ""
            if (type == "RICH_TEXT_NODE_TYPE_EMOJI") {
                val emoji = node.emoji
                val url = emoji?.icon_url?.fixUrl()
                val size = emoji?.size ?: 1
                if (!url.isNullOrEmpty()) {
                    emotes[text] = Emote(
                        id = 0,
                        packageId = 0,
                        name = text,
                        alias = text,
                        url = url,
                        size = size
                    )
                }
            } else if (type == "RICH_TEXT_NODE_TYPE_AT") {
                val rid = node.rid ?: ""
                val mid = rid.toLongOrNull()
                if (mid != null) {
                    val name = text.removePrefix("@").trim()
                    members[name] = mid
                }
            }
        }
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        return HttpClient.client.newCall(request).execute().body?.string() ?: ""
    }

    private fun String.fixUrl(): String = when {
        startsWith("//") -> "https:$this"
        startsWith("http://") -> replaceFirst("http://", "https://")
        else -> this
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
}
