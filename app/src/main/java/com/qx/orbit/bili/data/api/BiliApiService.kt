package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.*
import com.google.gson.JsonElement
import com.qx.orbit.bili.data.sign.AppSign
import retrofit2.http.*

interface BiliApiService {

    // ===== Video =====

    @GET("https://api.bilibili.com/x/web-interface/view")
    suspend fun getVideoInfo(
        @Query("bvid") bvid: String? = null,
        @Query("aid") aid: Long? = null
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/tag/archive/tags")
    suspend fun getVideoTags(
        @Query("bvid") bvid: String? = null,
        @Query("aid") aid: Long? = null
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/player/online/total")
    suspend fun getWatching(@Query("aid") aid: Long, @Query("cid") cid: Long): Result<JsonElement>

    @GET("https://api.bilibili.com/x/player/wbi/playurl")
    suspend fun getPlayUrl(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.bilibili.com/x/player/wbi/v2")
    suspend fun getPlayerV2(@Query("aid") aid: Long, @Query("cid") cid: Long): Result<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/archive/relation")
    suspend fun getVideoStats(@Query("aid") aid: Long): Result<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/archive/related")
    suspend fun getRelated(@Query("aid") aid: Long): Result<JsonElement>

    // ===== Recommend / Popular / Ranking =====

    @GET("https://api.bilibili.com/x/web-interface/popular")
    suspend fun getPopular(
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int = 10
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/ranking/v2")
    suspend fun getRanking(@Query("rid") rid: Int, @Query("type") type: String): Result<JsonElement>

    // ===== User =====

    @GET("https://api.bilibili.com/x/web-interface/card")
    suspend fun getUserCard(@Query("mid") mid: Long): Result<JsonElement>

    @GET("https://api.bilibili.com/x/space/myinfo")
    suspend fun getMyInfo(): Result<JsonElement>

    @GET("https://account.bilibili.com/site/getCoin")
    suspend fun getCoin(): Result<JsonElement>

    @GET("https://api.bilibili.com/x/space/wbi/arc/search")
    suspend fun getUserVideos(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.bilibili.com/x/space/wbi/article")
    suspend fun getUserArticles(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.bilibili.com/x/relation/followings")
    suspend fun getFollowingList(
        @Query("vmid") vmid: Long,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20,
        @Query("order") order: String = "desc"
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/relation/followers")
    suspend fun getFollowerList(
        @Query("vmid") vmid: Long,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20,
        @Query("order") order: String = "desc"
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/relation/tags")
    suspend fun getFollowTags(): Result<JsonElement>

    @GET("https://api.bilibili.com/x/relation/tag")
    suspend fun getFollowTagUsers(
        @Query("tagid") tagid: Int,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/relation/modify")
    suspend fun followUser(
        @Field("fid") fid: Long,
        @Field("act") act: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    // ===== Like / Coin / Favorite =====

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/web-interface/archive/like")
    suspend fun like(
        @Field("aid") aid: Long,
        @Field("like") like: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/web-interface/coin/add")
    suspend fun coin(
        @Field("aid") aid: Long,
        @Field("multiply") multiply: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/medialist/gateway/coll/resource/deal")
    suspend fun favorite(
        @Field("rid") rid: Long,
        @Field("media_id") mediaId: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/web-interface/archive/like/triple")
    suspend fun triple(@Field("aid") aid: Long, @Field("csrf") csrf: String): Result<JsonElement>

    // ===== Reply =====

    @GET("https://api.bilibili.com/x/v2/reply")
    suspend fun getReplies(
        @Query("oid") oid: Long,
        @Query("type") type: Int,
        @Query("pn") pn: Int,
        @Query("sort") sort: Int,
        @Query("root") root: Long = 0
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/v2/reply/reply")
    suspend fun getSubReplies(
        @Query("oid") oid: Long,
        @Query("type") type: Int,
        @Query("pn") pn: Int,
        @Query("root") root: Long
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/v2/reply/wbi/main")
    suspend fun getRepliesLazy(@QueryMap params: Map<String, String>): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/reply/add")
    suspend fun sendReply(
        @Field("oid") oid: Long,
        @Field("root") root: Long,
        @Field("parent") parent: Long,
        @Field("message") message: String,
        @Field("type") type: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/reply/action")
    suspend fun likeReply(
        @Field("oid") oid: Long,
        @Field("rpid") rpid: Long,
        @Field("action") action: Int,
        @Field("type") type: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/v2/reply/count")
    suspend fun getReplyCount(
        @Query("oid") oid: Long,
        @Query("type") type: Int
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/reply/del")
    suspend fun deleteReply(
        @Field("oid") oid: Long,
        @Field("rpid") rpid: Long,
        @Field("type") type: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    // ===== Dynamic =====

    @GET("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all")
    suspend fun getDynamicList(
        @Query("type") type: String,
        @Query("offset") offset: String
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/polymer/web-dynamic/v1/detail")
    suspend fun getDynamic(@Query("id") id: Long): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.vc.bilibili.com/dynamic_like/v1/dynamic_like/thumb")
    suspend fun likeDynamic(
        @Field("dynamic_id") dynamicId: String,
        @Field("up") up: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    // ===== History =====

    @GET("https://api.bilibili.com/x/web-interface/history/cursor")
    suspend fun getHistory(
        @Query("type") type: String,
        @Query("view_at") viewAt: Long,
        @Query("business") business: String,
        @Query("max") max: Long
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/history/report")
    suspend fun reportHistory(
        @Field("aid") aid: Long,
        @Field("cid") cid: Long,
        @Field("progress") progress: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    // ===== Watch Later =====

    @GET("https://api.bilibili.com/x/v2/history/toview/web")
    suspend fun getWatchLater(): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/history/toview/add")
    suspend fun addWatchLater(
        @Field("aid") aid: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/history/toview/del")
    suspend fun deleteWatchLater(
        @Field("aid") aid: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    // ===== Search =====

    @FormUrlEncoded
    @POST("https://api.bilibili.com/pgc/web/follow/add")
    suspend fun addBangumiFollow(
        @Field("season_id") seasonId: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/pgc/web/follow/del")
    suspend fun deleteBangumiFollow(
        @Field("season_id") seasonId: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/wbi/search/all/v2")
    suspend fun searchAll(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.bilibili.com/x/web-interface/wbi/search/type")
    suspend fun searchType(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://s.search.bilibili.com/main/suggest")
    suspend fun getSearchSuggest(@Query("term") term: String): JsonElement

    @GET("https://api.bilibili.com/x/web-interface/wbi/search/default")
    suspend fun getDefaultSearch(): Result<JsonElement>

    // ===== Favorite =====

    @GET("https://api.bilibili.com/x/v3/fav/folder/created/list-all")
    suspend fun getFavFolders(
        @Query("type") type: Int,
        @Query("up_mid") upMid: Long
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/v3/fav/folder/collected/list")
    suspend fun getCollectedFolders(
        @Query("up_mid") upMid: Long,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 10
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/space/fav/arc")
    suspend fun getFavVideos(
        @Query("vmid") vmid: Long,
        @Query("fid") fid: Long,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 30
    ): Result<JsonElement>

    // ===== Live =====

    @GET("https://api.live.bilibili.com/xlive/web-interface/v1/second/getUserRecommend")
    suspend fun getLiveRecommend(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList")
    suspend fun getLiveFollowed(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 10
    ): Result<JsonElement>

    @GET("https://api.live.bilibili.com/room/v1/Room/get_info")
    suspend fun getLiveRoomInfo(@Query("room_id") roomId: Long): Result<JsonElement>

    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getLivePlayInfo(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo")
    suspend fun getDanmuInfo(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.live.bilibili.com/xlive/web-ucenter/v2/emoticon/GetEmoticons")
    suspend fun getLiveEmoticons(
        @Query("platform") platform: String,
        @Query("room_id") roomId: Long
    ): Result<JsonElement>

    // ===== Bangumi =====

    @GET("https://api.bilibili.com/pgc/review/user")
    suspend fun getBangumiReview(@Query("media_id") mediaId: Long): Result<JsonElement>

    @GET("https://api.bilibili.com/pgc/view/web/season")
    suspend fun getSeasonInfo(
        @Query("season_id") seasonId: Long? = null,
        @Query("ep_id") epId: Long? = null
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/pgc/view/web/season/user/status")
    suspend fun getSeasonUserStatus(
        @Query("season_id") seasonId: Long? = null,
        @Query("ep_id") epId: Long? = null
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/pgc/web/season/section")
    suspend fun getSeasonSection(@Query("season_id") seasonId: Long): Result<JsonElement>

    @GET("https://api.bilibili.com/pgc/web/timeline")
    suspend fun getTimeline(
        @Query("types") types: String,
        @Query("before") before: Int,
        @Query("after") after: Int
    ): Result<JsonElement>

    // ===== Message =====

    @GET("https://api.bilibili.com/x/msgfeed/unread")
    suspend fun getUnread(): Result<JsonElement>

    @GET("https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread")
    suspend fun getPrivateMsgUnread(): Result<JsonElement>

    @GET("https://api.bilibili.com/x/msgfeed/like")
    suspend fun getLikeMsg(
        @Query("id") id: Long,
        @Query("reply_time") replyTime: Long
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/msgfeed/reply")
    suspend fun getReplyMsg(
        @Query("id") id: Long,
        @Query("reply_time") replyTime: Long
    ): Result<JsonElement>

    @GET("https://api.bilibili.com/x/msgfeed/at")
    suspend fun getAtMsg(@Query("id") id: Long, @Query("at_time") atTime: Long): Result<JsonElement>

    // ===== Emote =====

    @GET("https://api.bilibili.com/x/emote/user/panel/web")
    suspend fun getEmotes(@Query("business") business: String): Result<JsonElement>

    // ===== Danmaku =====

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/dm/post")
    suspend fun sendDanmaku(
        @Field("oid") oid: Long,
        @Field("msg") msg: String,
        @Field("bvid") bvid: String,
        @Field("progress") progress: Long,
        @Field("color") color: Int,
        @Field("mode") mode: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    // ===== Article =====

    @GET("https://api.bilibili.com/x/article/view")
    suspend fun getArticle(@Query("id") id: Long): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/article/like")
    suspend fun likeArticle(
        @Field("id") id: Long,
        @Field("type") type: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/web-interface/coin/add")
    suspend fun coinArticle(
        @Field("aid") aid: Long,
        @Field("upid") upid: Long,
        @Field("multiply") multiply: Int,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/article/favorites/add")
    suspend fun favoriteArticle(
        @Field("id") id: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/article/favorites/del")
    suspend fun delFavoriteArticle(
        @Field("id") id: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    // ===== Account =====

    @GET("https://api.bilibili.com/x/vip/privilege/my")
    suspend fun getVipInfo(): Result<JsonElement>

    @GET("https://api.bilibili.com/x/member/web/exp/log")
    suspend fun getExpLog(): Result<JsonElement>

    @GET("https://api.bilibili.com/x/member/web/coin/log")
    suspend fun getCoinLog(): Result<JsonElement>

    /**
     * HD/TV 扫码登录后激活 cookie;否则调 /nav 会被服务端判未登录。
     * payload 为 B 站加密串(目前为预留,未调用时传空字符串)。
     */
    @POST("https://api.bilibili.com/x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activeCookie(@Body payload: com.google.gson.JsonObject): Result<JsonElement>

    // ===== Series =====

    @GET("https://api.bilibili.com/x/polymer/web-space/seasons_series_list")
    suspend fun getUserSeries(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.bilibili.com/x/series/archives")
    suspend fun getSeriesArchives(@QueryMap params: Map<String, String>): Result<JsonElement>

    @GET("https://api.bilibili.com/x/polymer/web-space/seasons_archives_list")
    suspend fun getSeasonsArchives(@QueryMap params: Map<String, String>): Result<JsonElement>

    // ===== Electric =====

    @GET("https://api.bilibili.com/x/ugcpay-rank/elec/month/up")
    suspend fun getElectricPanel(@Query("up_mid") upMid: Long): Result<JsonElement>

    // ===== Interaction =====

    @GET("https://api.bilibili.com/x/stein/edgeinfo_v2")
    suspend fun getEdgeInfo(
        @Query("aid") aid: Long,
        @Query("bvid") bvid: String,
        @Query("graph_version") graphVersion: Long,
        @Query("edge_id") edgeId: Long
    ): Result<JsonElement>

    // ===== HD / TV Login =====

    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    suspend fun getLoginQr(): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/web/sso/list")
    suspend fun requestSSOs(): Result<JsonElement>

    @FormUrlEncoded
    @AppSign
    @POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
    suspend fun getTvAuthCode(
        @Field("local_id") localId: String = "0",
        @Field("mobi_app") mobiApp: String = "android_hd",
        @Field("platform") platform: String = "android"
    ): Result<JsonElement>

    @FormUrlEncoded
    @AppSign
    @POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
    suspend fun pollTvQrCode(
        @Field("auth_code") authCode: String,
        @Field("local_id") localId: String = "0",
        @Field("mobi_app") mobiApp: String = "android_hd",
        @Field("platform") platform: String = "android"
    ): Result<JsonElement>

    // ===== Login Record =====

    @GET("https://api.bilibili.com/x/safecenter/login_notice")
    suspend fun getLoginRecord(
        @Query("mid") mid: Long,
        @Query("buvid") buvid: String
    ): Result<JsonElement>

    // ===== Heartbeat =====

    @FormUrlEncoded
    @POST
    suspend fun reportHeartbeat(
        @Url url: String,
        @Field("aid") aid: Long,
        @Field("bvid") bvid: String,
        @Field("cid") cid: Long,
        @Field("mid") mid: Long,
        @Field("csrf") csrf: String,
        @Field("played_time") playedTime: Long,
        @Field("realtime") realtime: Long,
        @Field("start_ts") startTs: Long,
        @Field("type") type: String = "3",
        @Field("sub_type") subType: String? = null,
        @Field("epid") epid: Long? = null,
        @Field("sid") sid: Long? = null,
        @Field("video_duration") videoDuration: Long = 0,
        @Field("last_play_progress_time") lastPlayProgressTime: Long = 0,
        @Field("dt") dt: String = "2",
        @Field("play_type") playType: String = "0"
    ): Result<JsonElement>

    // ===== Creative Center =====

    @GET("https://member.bilibili.com/x/web/index/stat")
    suspend fun getVideoStat(): Result<JsonElement>

    @GET("https://member.bilibili.com/x/web/index/scrolls")
    suspend fun getBeUPTime(): Result<JsonElement>

    // ===== WBI Nav =====

    @GET("https://api.bilibili.com/x/web-interface/nav")
    suspend fun getNav(): Result<JsonElement>

    // ===== Cookie Refresh =====

    @GET("https://passport.bilibili.com/x/passport-login/web/cookie/info")
    suspend fun cookieInfo(): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/web/cookie/refresh")
    suspend fun refreshCookie(@Field("csrf") csrf: String): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/web/confirm/refresh")
    suspend fun confirmRefreshCookie(@Field("csrf") csrf: String): Result<JsonElement>

    // ===== Buvid / Ticket =====

    @GET("https://api.bilibili.com/x/web-frontend/getbuvid")
    suspend fun getBuvid3Only(): Result<JsonElement>

    @GET("https://api.bilibili.com/x/frontend/finger/spi")
    suspend fun getWebBuvids(): Result<JsonElement>

    @POST("https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket")
    suspend fun genBiliTicket(@Body body: com.google.gson.JsonObject): Result<JsonElement>

    // ===== VIP =====

    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/vip/experience/add")
    suspend fun addVipExperience(@Field("csrf") csrf: String): Result<JsonElement>

    // ===== Private Message =====

    @GET("https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs")
    suspend fun getPrivateMsg(
        @Query("talker_id") talkerId: Long,
        @Query("session_type") sessionType: Int = 1,
        @Query("size") size: Int,
        @Query("begin_seqno") beginSeqno: Long,
        @Query("end_seqno") endSeqno: Long
    ): Result<JsonElement>

    @GET("https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions")
    suspend fun getSessions(
        @Query("size") size: Int,
        @Query("session_type") sessionType: Int = 1
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.vc.bilibili.com/web_im/v1/web_im/send_msg")
    suspend fun sendPrivateMsg(
        @Field("msg_type") msgType: Int,
        @Field("msg_content") content: String,
        @Field("sender_uid") senderUid: Long,
        @Field("receiver_uid") receiverUid: Long,
        @Field("timestamp") timestamp: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    @FormUrlEncoded
    @POST("https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack")
    suspend fun updateAck(
        @Field("talker_id") talkerId: Long,
        @Field("session_type") sessionType: Int,
        @Field("ack_seqno") ackSeqno: Long,
        @Field("csrf") csrf: String
    ): Result<JsonElement>

    companion object {
        fun create(): BiliApiService {
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("https://api.bilibili.com/")
                .client(HttpClient.client)
                .addCallAdapterFactory(ResultCallAdapterFactory())
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(GsonConfig.gson))
                .build()
            return retrofit.create(BiliApiService::class.java)
        }
    }
}
