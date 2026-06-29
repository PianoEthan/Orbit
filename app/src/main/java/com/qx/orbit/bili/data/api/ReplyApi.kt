package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qx.orbit.bili.util.formatBiliTime
import java.util.*

object ReplyApi {

    private val api by lazy { BiliApiService.create() }

    const val REPLY_TYPE_VIDEO = 1
    const val REPLY_TYPE_VIDEO_CHILD = 0
    const val REPLY_TYPE_ARTICLE = 12
    const val REPLY_TYPE_DYNAMIC = 17
    const val REPLY_TYPE_DYNAMIC_CHILD = 11

    internal data class ReplyListData(
        @SerializedName("replies") val replies: List<ReplyRootData>? = null,
        @SerializedName("top_replies") val top_replies: List<ReplyRootData>? = null,
        @SerializedName("page") val page: PageData? = null
    )

    internal data class PageData(
        @SerializedName("num") val num: Int = 0,
        @SerializedName("size") val size: Int = 0,
        @SerializedName("count") val count: Int = 0
    )

    internal data class ReplyLazyData(
        @SerializedName("replies") val replies: List<ReplyRootData>? = null,
        @SerializedName("top_replies") val top_replies: List<ReplyRootData>? = null,
        @SerializedName("cursor") val cursor: CursorData? = null
    )

    internal data class CursorData(
        @SerializedName("all_count") val all_count: Int = 0,
        @SerializedName("next") val next: Int = 0,
        @SerializedName("is_end") val is_end: Boolean = false,
        @SerializedName("pagination_reply") val pagination_reply: PaginationReply? = null
    )

    internal data class PaginationReply(
        @SerializedName("next_offset") val next_offset: String? = null
    )

    internal data class ReplyRootData(
        @SerializedName("rpid") val rpid: Long = 0,
        @SerializedName("oid") val oid: Long = 0,
        @SerializedName("root") val root: Long = 0,
        @SerializedName("parent") val parent: Long = 0,
        @SerializedName("ctime") val ctime: Long = 0,
        @SerializedName("member") val member: MemberData? = null,
        @SerializedName("content") val content: ContentData? = null,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("reply_control") val reply_control: ReplyControlData? = null,
        @SerializedName("replies") val replies: List<ReplyRootData>? = null,
        @SerializedName("rcount") val rcount: Int = 0,
        @SerializedName("up_action") val up_action: UpActionData? = null,
        @SerializedName("reply_replies") val reply_replies: List<ReplyRootData>? = null,
        @SerializedName("action") val action: Int = 0
    )

    internal data class MemberData(
        @SerializedName("mid") val mid: String? = null,
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("avatar") val avatar: String? = null,
        @SerializedName("sign") val sign: String? = null,
        @SerializedName("level_info") val level_info: LevelInfoData? = null,
        @SerializedName("official_verify") val official_verify: OfficialVerifyData? = null,
        @SerializedName("vip") val vip: VipData? = null,
        @SerializedName("user_senior") val user_senior: SeniorData? = null
    )

    internal data class LevelInfoData(
        @SerializedName("current_level") val current_level: Int = 0
    )

    internal data class OfficialVerifyData(
        @SerializedName("type") val type: Int = -1,
        @SerializedName("desc") val desc: String? = null
    )

    internal data class VipData(
        @SerializedName("vipStatus") val vipStatus: Int = 0,
        @SerializedName("nickname_color") val nickname_color: String? = null
    )

    internal data class SeniorData(
        @SerializedName("status") val status: Int = 0
    )

    internal data class ContentData(
        @SerializedName("message") val message: String? = null,
        @SerializedName("pictures") val pictures: List<PictureData>? = null,
        @SerializedName("emote") val emote: Map<String, EmoteData>? = null,
        @SerializedName("members") val members: List<MemberData>? = null
    )

    internal data class EmoteData(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("package_id") val packageId: Int = 0,
        @SerializedName("text") val text: String? = null,
        @SerializedName("url") val url: String? = null,
        @SerializedName("meta") val meta: EmoteMetaData? = null
    )

    internal data class EmoteMetaData(
        @SerializedName("size") val size: Int = 1,
        @SerializedName("alias") val alias: String? = null
    )

    internal data class PictureData(
        @SerializedName("img_src") val img_src: String? = null
    )

    internal data class ReplyControlData(
        @SerializedName("is_top") val is_top: Boolean = false
    )

    internal data class UpActionData(
        @SerializedName("like") val like: Boolean = false,
        @SerializedName("reply") val reply: Boolean = false
    )

    internal data class ReplyCountData(
        @SerializedName("count") val count: Long = 0
    )

    suspend fun getReplies(
        oid: Long,
        rpid: Long = 0,
        pageNumber: Int = 1,
        type: Int = REPLY_TYPE_VIDEO,
        sort: Int = 1
    ): List<Reply> = withContext(Dispatchers.IO) {
        android.util.Log.d("BiliApi", "getReplies oid=$oid rpid=$rpid page=$pageNumber type=$type sort=$sort")
        val jsonElement = if (rpid > 0) {
            when (val result = api.getSubReplies(oid, type, pageNumber, rpid)) {
                is Result.Success -> result.data
                is Result.Error -> {
                    android.util.Log.e("BiliApi", "getSubReplies error: ${result.exception.code} ${result.exception.message}")
                    return@withContext emptyList()
                }
            }
        } else {
            when (val result = api.getReplies(oid, type, pageNumber, sort, 0)) {
                is Result.Success -> result.data
                is Result.Error -> {
                    android.util.Log.e("BiliApi", "getReplies error: ${result.exception.code} ${result.exception.message}")
                    return@withContext emptyList()
                }
            }
        }
        val typeToken = object : TypeToken<ApiResponse<ReplyListData>>() {}.type
        val resp: ApiResponse<ReplyListData>? = GsonConfig.gson.fromJson(jsonElement, typeToken)
        if (resp == null || !resp.isSuccess || resp.data == null) {
            android.util.Log.w("BiliApi", "getReplies parse failed: code=${resp?.code}")
            return@withContext emptyList()
        }
        android.util.Log.d("BiliApi", "getReplies replies=${resp.data.replies?.size} top=${resp.data.top_replies?.size}")
        val isDynamic = type == REPLY_TYPE_DYNAMIC || type == REPLY_TYPE_DYNAMIC_CHILD
        val normalList = resp.data.replies?.filterNotNull()?.map { parseReply(it, isDynamic, oid) } ?: emptyList()
        if (pageNumber == 1) {
            val topList = resp.data.top_replies?.filterNotNull()?.map { parseReply(it, isDynamic, oid) } ?: emptyList()
            val topIds = topList.map { it.rpid }.toSet()
            android.util.Log.d("BiliApi", "getReplies result: top=${topList.size} normal=${normalList.size}")
            topList + normalList.filter { it.rpid !in topIds }
        } else {
            normalList
        }
    }

    suspend fun getRepliesLazy(
        oid: Long,
        rpid: Long = 0,
        pagination: String? = null,
        type: Int = REPLY_TYPE_VIDEO,
        sort: Int = 1
    ): Triple<Int, String?, List<Reply>> = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "oid" to oid.toString(),
            "type" to type.toString(),
            "mode" to sort.toString()
        )
        if (rpid > 0) params["seek_rpid"] = rpid.toString()
        if (!pagination.isNullOrEmpty()) params["pagination_str"] = "{\"offset\":\"$pagination\"}"
        android.util.Log.d("BiliApi", "getRepliesLazy params=$params")
        val signedParams = WbiSigner.signParams(params)
        android.util.Log.d("BiliApi", "getRepliesLazy signed=$signedParams")
        val jsonElement = when (val result = api.getRepliesLazy(signedParams)) {
            is Result.Success -> result.data
            is Result.Error -> {
                android.util.Log.e("BiliApi", "getRepliesLazy error: ${result.exception.code} ${result.exception.message}")
                return@withContext Triple(0, null, emptyList())
            }
        }
        android.util.Log.d("BiliApi", "getRepliesLazy response: ${jsonElement.toString().take(500)}")
        val typeToken = object : TypeToken<ApiResponse<ReplyLazyData>>() {}.type
        val resp: ApiResponse<ReplyLazyData>? = GsonConfig.gson.fromJson(jsonElement, typeToken)
        if (resp == null || !resp.isSuccess || resp.data == null) {
            android.util.Log.w("BiliApi", "getRepliesLazy parse failed: resp=$resp code=${resp?.code}")
            return@withContext Triple(0, null, emptyList())
        }
        android.util.Log.d("BiliApi", "getRepliesLazy replies=${resp.data.replies?.size} top=${resp.data.top_replies?.size}")
        val isDynamic = type == REPLY_TYPE_DYNAMIC || type == REPLY_TYPE_DYNAMIC_CHILD
        val count = resp.data.cursor?.all_count ?: 0
        val nextOffset = resp.data.cursor?.pagination_reply?.next_offset
        val topList = resp.data.top_replies?.filterNotNull()?.map { parseReply(it, isDynamic, oid) } ?: emptyList()
        val normalList = resp.data.replies?.filterNotNull()?.map { parseReply(it, isDynamic, oid) } ?: emptyList()
        val topIds = topList.map { it.rpid }.toSet()
        val filtered = normalList.filter { it.rpid !in topIds && it.rpid != rpid }
        Triple(count, nextOffset, topList + filtered)
    }

    suspend fun sendReply(
        oid: Long,
        root: Long = 0,
        parent: Long = 0,
        text: String,
        type: Int = REPLY_TYPE_VIDEO
    ): Pair<Int, Reply?> = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.sendReply(oid, root, parent, text, type, CookieManager.getCsrf())) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext Pair(result.exception.code, null)
        }
        val typeToken = object : TypeToken<ApiResponse<ReplyRootData>>() {}.type
        val resp: ApiResponse<ReplyRootData>? = GsonConfig.gson.fromJson(jsonElement, typeToken)
        if (resp == null) return@withContext Pair(-1, null)
        if (!resp.isSuccess) return@withContext Pair(resp.code, null)
        val isDynamic = type == REPLY_TYPE_DYNAMIC || type == REPLY_TYPE_DYNAMIC_CHILD
        val reply = resp.data?.let { parseReply(it, isDynamic, oid) }
        Pair(0, reply)
    }

    suspend fun likeReply(oid: Long, rpid: Long, action: Int, type: Int = 1): Int = withContext(Dispatchers.IO) {
        when (val result = api.likeReply(oid, rpid, action, type, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> result.exception.code
        }
    }

    suspend fun deleteReply(oid: Long, rpid: Long, type: Int = REPLY_TYPE_VIDEO): Int = withContext(Dispatchers.IO) {
        when (val result = api.deleteReply(oid, rpid, type, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> result.exception.code
        }
    }

    suspend fun getReplyCount(oid: Long, type: Int = REPLY_TYPE_VIDEO): Long = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.getReplyCount(oid, type)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext 0L
        }
        val typeToken = object : TypeToken<ApiResponse<ReplyCountData>>() {}.type
        val resp: ApiResponse<ReplyCountData>? = GsonConfig.gson.fromJson(jsonElement, typeToken)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext 0L
        resp.data.count
    }

    private fun parseReply(data: ReplyRootData, isDynamic: Boolean, oid: Long): Reply {
        val mid = data.member?.mid?.toLongOrNull() ?: 0
        val sender = UserInfo(
            mid = mid,
            name = data.member?.uname ?: "",
            avatar = data.member?.avatar ?: "",
            sign = data.member?.sign ?: "",
            level = data.member?.level_info?.current_level ?: 0,
            official = data.member?.official_verify?.type ?: -1,
            officialDesc = data.member?.official_verify?.desc ?: "",
            vip_role = data.member?.vip?.vipStatus ?: 0,
            vip_nickname_color = data.member?.vip?.nickname_color ?: "",
            is_senior_member = data.member?.user_senior?.status ?: 0
        )
        val pictures = data.content?.pictures?.filterNotNull()?.map { it.img_src ?: "" } ?: emptyList()
        val pubTime = if (data.ctime > 0) {
            formatBiliTime(data.ctime)
        } else ""
        val childList = data.replies?.filterNotNull()?.map { parseReply(it, isDynamic, oid) } ?: emptyList()
        return Reply(
            rpid = data.rpid,
            oid = data.oid,
            root = data.root,
            parent = data.parent,
            pubTime = pubTime,
            sender = sender,
            message = data.content?.message ?: "",
            pictureList = pictures,
            likeCount = data.like,
            upLiked = data.up_action?.like ?: false,
            upReplied = data.up_action?.reply ?: false,
            liked = data.action == 1,
            childCount = data.rcount,
            isDynamic = isDynamic,
            childMsgList = childList,
            isTop = data.reply_control?.is_top ?: false,
            emotes = data.content?.emote?.mapValues { 
                Emote(
                    id = it.value.id,
                    packageId = it.value.packageId,
                    name = it.key,
                    alias = it.value.meta?.alias ?: "",
                    url = it.value.url ?: "",
                    size = it.value.meta?.size ?: 1
                ) 
            } ?: emptyMap(),
            members = data.content?.members?.filter { it.uname != null && it.mid != null }?.associate { 
                it.uname!! to (it.mid!!.toLongOrNull() ?: 0L) 
            } ?: emptyMap()
        )
    }

}
