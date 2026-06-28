package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.google.gson.JsonElement
import retrofit2.http.*

interface BiliApiService {

    // ===== Video =====

    @GET("https://api.bilibili.com/x/web-interface/view")
    suspend fun getVideoInfo(@Query("bvid") bvid: String? = null, @Query("aid") aid: Long? = null): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/tag/archive/tags")
    suspend fun getVideoTags(@Query("bvid") bvid: String? = null, @Query("aid") aid: Long? = null): ApiResponse<List<JsonElement>>

    @GET("https://api.bilibili.com/x/player/online/total")
    suspend fun getWatching(@Query("aid") aid: Long, @Query("cid") cid: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/player/wbi/playurl")
    suspend fun getPlayUrl(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/player/wbi/v2")
    suspend fun getPlayerV2(@Query("aid") aid: Long, @Query("cid") cid: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/archive/relation")
    suspend fun getVideoStats(@Query("aid") aid: Long): ApiResponse<Stats>

    @GET("https://api.bilibili.com/x/web-interface/archive/related")
    suspend fun getRelated(@Query("aid") aid: Long): ApiResponse<List<JsonElement>>

    // ===== Recommend / Popular / Ranking =====

    @GET("https://api.bilibili.com/x/web-interface/popular")
    suspend fun getPopular(@Query("pn") page: Int, @Query("ps") pageSize: Int = 10): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/ranking/v2")
    suspend fun getRanking(@Query("rid") rid: Int, @Query("type") type: String): ApiResponse<JsonElement>

    // ===== User =====

    @GET("https://api.bilibili.com/x/web-interface/card")
    suspend fun getUserCard(@Query("mid") mid: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/space/myinfo")
    suspend fun getMyInfo(): ApiResponse<JsonElement>

    @GET("https://account.bilibili.com/site/getCoin")
    suspend fun getCoin(): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/space/wbi/arc/search")
    suspend fun getUserVideos(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/space/wbi/article")
    suspend fun getUserArticles(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/relation/modify")
    suspend fun followUser(@Field("fid") fid: Long, @Field("act") act: Int, @Field("csrf") csrf: String): ApiResponse<Unit>

    // ===== Like / Coin / Favorite =====

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/web-interface/archive/like")
    suspend fun like(@Field("aid") aid: Long, @Field("like") like: Int, @Field("csrf") csrf: String): ApiResponse<Unit>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/web-interface/coin/add")
    suspend fun coin(@Field("aid") aid: Long, @Field("multiply") multiply: Int, @Field("csrf") csrf: String): ApiResponse<Unit>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/medialist/gateway/coll/resource/deal")
    suspend fun favorite(@Field("rid") rid: Long, @Field("media_id") mediaId: Long, @Field("csrf") csrf: String): ApiResponse<Unit>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/web-interface/archive/like/triple")
    suspend fun triple(@Field("aid") aid: Long, @Field("csrf") csrf: String): ApiResponse<Unit>

    // ===== Reply =====

    @GET("https://api.bilibili.com/x/v2/reply")
    suspend fun getReplies(@Query("oid") oid: Long, @Query("type") type: Int, @Query("pn") pn: Int, @Query("sort") sort: Int): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/v2/reply/wbi/main")
    suspend fun getRepliesLazy(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/reply/add")
    suspend fun sendReply(@Field("oid") oid: Long, @Field("root") root: Long, @Field("parent") parent: Long, @Field("message") message: String, @Field("type") type: Int, @Field("csrf") csrf: String): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/reply/action")
    suspend fun likeReply(@Field("oid") oid: Long, @Field("rpid") rpid: Long, @Field("action") action: Int, @Field("csrf") csrf: String): ApiResponse<Unit>

    @GET("https://api.bilibili.com/x/v2/reply/count")
    suspend fun getReplyCount(@Query("oid") oid: Long, @Query("type") type: Int): ApiResponse<JsonElement>

    // ===== Dynamic =====

    @GET("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all")
    suspend fun getDynamicList(@Query("type") type: String, @Query("offset") offset: String): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/polymer/web-dynamic/v1/detail")
    suspend fun getDynamic(@Query("id") id: Long): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("https://api.vc.bilibili.com/dynamic_like/v1/dynamic_like/thumb")
    suspend fun likeDynamic(@Field("dynamic_id") dynamicId: String, @Field("up") up: Int, @Field("csrf") csrf: String): ApiResponse<Unit>

    // ===== History =====

    @GET("https://api.bilibili.com/x/web-interface/history/cursor")
    suspend fun getHistory(@Query("type") type: String, @Query("view_at") viewAt: Long, @Query("business") business: String, @Query("max") max: Long): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/history/report")
    suspend fun reportHistory(@Field("aid") aid: Long, @Field("cid") cid: Long, @Field("progress") progress: Long, @Field("csrf") csrf: String): ApiResponse<Unit>

    // ===== Watch Later =====

    @GET("https://api.bilibili.com/x/v2/history/toview/web")
    suspend fun getWatchLater(): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/history/toview/add")
    suspend fun addWatchLater(@Field("aid") aid: Long, @Field("csrf") csrf: String): ApiResponse<Unit>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/history/toview/del")
    suspend fun deleteWatchLater(@Field("aid") aid: Long, @Field("csrf") csrf: String): ApiResponse<Unit>

    // ===== Search =====

    @GET("https://api.bilibili.com/x/web-interface/wbi/search/all/v2")
    suspend fun searchAll(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/wbi/search/type")
    suspend fun searchType(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    @GET("https://s.search.bilibili.com/main/suggest")
    suspend fun getSearchSuggest(@Query("term") term: String): JsonElement

    @GET("https://api.bilibili.com/x/web-interface/wbi/search/default")
    suspend fun getDefaultSearch(): ApiResponse<JsonElement>

    // ===== Favorite =====

    @GET("https://api.bilibili.com/x/v3/fav/folder/created/list-all")
    suspend fun getFavFolders(@Query("type") type: Int, @Query("up_mid") upMid: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/v3/fav/folder/collected/list")
    suspend fun getCollectedFolders(@Query("up_mid") upMid: Long, @Query("pn") pn: Int, @Query("ps") ps: Int = 10): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/space/fav/arc")
    suspend fun getFavVideos(@Query("vmid") vmid: Long, @Query("fid") fid: Long, @Query("pn") pn: Int, @Query("ps") ps: Int = 30): ApiResponse<JsonElement>

    // ===== Live =====

    @GET("https://api.live.bilibili.com/xlive/web-interface/v1/second/getUserRecommend")
    suspend fun getLiveRecommend(@Query("page") page: Int, @Query("page_size") pageSize: Int = 10): ApiResponse<JsonElement>

    @GET("https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList")
    suspend fun getLiveFollowed(@Query("page") page: Int, @Query("page_size") pageSize: Int = 10): ApiResponse<JsonElement>

    @GET("https://api.live.bilibili.com/room/v1/Room/get_info")
    suspend fun getLiveRoomInfo(@Query("room_id") roomId: Long): ApiResponse<JsonElement>

    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getLivePlayInfo(@Query("room_id") roomId: Long, @Query("qn") qn: Int): ApiResponse<JsonElement>

    // ===== Bangumi =====

    @GET("https://api.bilibili.com/pgc/review/user")
    suspend fun getBangumiReview(@Query("media_id") mediaId: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/pgc/view/web/season")
    suspend fun getSeasonInfo(@Query("season_id") seasonId: Long? = null, @Query("ep_id") epId: Long? = null): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/pgc/web/season/section")
    suspend fun getSeasonSection(@Query("season_id") seasonId: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/pgc/web/timeline")
    suspend fun getTimeline(@Query("types") types: String, @Query("before") before: Int, @Query("after") after: Int): ApiResponse<JsonElement>

    // ===== Message =====

    @GET("https://api.bilibili.com/x/msgfeed/unread")
    suspend fun getUnread(): ApiResponse<JsonElement>

    @GET("https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread")
    suspend fun getPrivateMsgUnread(): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/msgfeed/like")
    suspend fun getLikeMsg(@Query("id") id: Long, @Query("reply_time") replyTime: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/msgfeed/reply")
    suspend fun getReplyMsg(@Query("id") id: Long, @Query("reply_time") replyTime: Long): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/msgfeed/at")
    suspend fun getAtMsg(@Query("id") id: Long, @Query("at_time") atTime: Long): ApiResponse<JsonElement>

    // ===== Emote =====

    @GET("https://api.bilibili.com/x/emote/user/panel/web")
    suspend fun getEmotes(@Query("business") business: String): ApiResponse<JsonElement>

    // ===== Danmaku =====

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/dm/post")
    suspend fun sendDanmaku(@Field("oid") oid: Long, @Field("msg") msg: String, @Field("bvid") bvid: String, @Field("progress") progress: Long, @Field("color") color: Int, @Field("mode") mode: Int, @Field("csrf") csrf: String): ApiResponse<Unit>

    // ===== Article =====

    @GET("https://api.bilibili.com/x/article/view")
    suspend fun getArticle(@Query("id") id: Long): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/article/like")
    suspend fun likeArticle(@Field("id") id: Long, @Field("type") type: Int, @Field("csrf") csrf: String): ApiResponse<Unit>

    // ===== Account =====

    @GET("https://api.bilibili.com/x/vip/privilege/my")
    suspend fun getVipInfo(): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/member/web/exp/log")
    suspend fun getExpLog(): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/member/web/coin/log")
    suspend fun getCoinLog(): ApiResponse<JsonElement>

    /**
     * HD/TV 扫码登录后激活 cookie;否则调 /nav 会被服务端判未登录。
     * payload 为 B 站加密串(目前为预留,未调用时传空字符串)。
     */
    @POST("https://api.bilibili.com/x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activeCookie(@Body payload: com.google.gson.JsonObject): ApiResponse<JsonElement>

    // ===== Series =====

    @GET("https://api.bilibili.com/x/polymer/web-space/seasons_series_list")
    suspend fun getUserSeries(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    @GET("https://api.bilibili.com/x/series/archives")
    suspend fun getSeriesArchives(@QueryMap params: Map<String, String>): ApiResponse<JsonElement>

    // ===== Electric =====

    @GET("https://api.bilibili.com/x/ugcpay-rank/elec/month/up")
    suspend fun getElectricPanel(@Query("up_mid") upMid: Long): ApiResponse<JsonElement>

    // ===== Interaction =====

    @GET("https://api.bilibili.com/x/stein/edgeinfo_v2")
    suspend fun getEdgeInfo(@Query("aid") aid: Long, @Query("bvid") bvid: String, @Query("graph_version") graphVersion: Long, @Query("edge_id") edgeId: Long): ApiResponse<JsonElement>

    companion object {
        fun create(): BiliApiService {
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("https://api.bilibili.com/")
                .client(HttpClient.client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(GsonConfig.gson))
                .build()
            return retrofit.create(BiliApiService::class.java)
        }
    }
}
