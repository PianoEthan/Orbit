package com.qx.orbit.bili.data.repository

import com.qx.orbit.bili.data.api.CookieRefreshApi
import com.qx.orbit.bili.data.api.CookiesApi
import com.qx.orbit.bili.data.api.RecommendApi
import com.qx.orbit.bili.data.api.UserInfoApi
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.data.remote.CookieManager

/**
 * Home data contract consumed by any presentation surface.
 * Implementations own API, cookie, and account details.
 */
interface HomeRepository {
    suspend fun initializeSession()

    suspend fun refreshSession()

    suspend fun loadAccount(): UserInfoApi.NavInfoData?

    suspend fun loadFeed(feed: HomeFeed, page: Int): List<VideoCard>

    suspend fun removeVideo(video: VideoCard)
}

enum class HomeFeed {
    RECOMMEND,
    POPULAR
}

class DefaultHomeRepository : HomeRepository {
    override suspend fun initializeSession() {
        if (!CookieManager.getCookie().contains("buvid3")) {
            CookiesApi.checkCookies()
        }
    }

    override suspend fun refreshSession() {
        if (CookieManager.getCookie().contains("SESSDATA")) {
            CookieRefreshApi.doCookieRefresh(auto = true)
        }
    }

    override suspend fun loadAccount(): UserInfoApi.NavInfoData? = UserInfoApi.getNavInfo()

    override suspend fun loadFeed(feed: HomeFeed, page: Int): List<VideoCard> = when (feed) {
        HomeFeed.RECOMMEND -> RecommendApi.getRecommend(page)
        HomeFeed.POPULAR -> RecommendApi.getPopular(page)
    }

    override suspend fun removeVideo(video: VideoCard) {
        RecommendApi.dislike(video.aid)
        if (video.mid > 0) {
            UserInfoApi.blockUser(video.mid)
        }
    }
}
