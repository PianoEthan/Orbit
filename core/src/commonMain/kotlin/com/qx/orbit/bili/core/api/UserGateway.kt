package com.qx.orbit.bili.core.api

data class CoreHistoryItem(
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String,
    val author: String,
    val progressSeconds: Long,
    val durationSeconds: Long,
    val progressPercent: Float,
)

data class CoreFavFolder(
    val id: Long,
    val title: String,
    val mediaCount: Int,
    val cover: String,
)

data class CoreFavVideoPage(val videos: List<CoreVideo>, val hasMore: Boolean)
data class CoreHistoryCursor(val max: Long = 0, val viewAt: Long = 0, val business: String = "")
data class CoreHistoryPage(val items: List<CoreHistoryItem>, val next: CoreHistoryCursor?)
data class CoreDynamicVideoPage(val videos: List<CoreVideo>, val nextOffset: String?)

data class CoreFollowUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String = "",
    val fans: Int = 0,
    val attribute: Int = 0,
)

data class CoreFollowPage(val items: List<CoreFollowUser>, val total: Int)

interface UserDataGateway {
    suspend fun loadHistory(cookie: String): List<CoreHistoryItem>
    suspend fun loadHistoryPage(cookie: String, cursor: CoreHistoryCursor = CoreHistoryCursor()): CoreHistoryPage = CoreHistoryPage(loadHistory(cookie), null)
    suspend fun loadWatchLater(cookie: String): List<CoreVideo>
    suspend fun changeWatchLater(aid: Long, add: Boolean, cookie: String, csrf: String): Unit = error("暂不支持修改稍后再看")
    suspend fun loadFavFolders(cookie: String, mid: Long): List<CoreFavFolder>
    suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int): List<CoreVideo>
    suspend fun loadFavVideoPage(cookie: String, folderId: Long, page: Int): CoreFavVideoPage {
        val videos = loadFavVideos(cookie, folderId, page)
        return CoreFavVideoPage(videos, videos.size >= 20)
    }
    suspend fun loadDynamicVideos(cookie: String, offset: String = ""): List<CoreVideo>
    suspend fun loadDynamicVideoPage(cookie: String, offset: String = ""): CoreDynamicVideoPage = CoreDynamicVideoPage(loadDynamicVideos(cookie, offset), null)
    suspend fun loadFollowingList(mid: Long, page: Int, cookie: String = ""): CoreFollowPage = error("暂不支持关注列表")
    suspend fun loadFollowerList(mid: Long, page: Int, cookie: String = ""): CoreFollowPage = error("暂不支持粉丝列表")
    suspend fun createFavFolder(title: String, intro: String, privacy: Int, cookie: String, csrf: String): Boolean = false
    suspend fun editFavFolder(mediaId: Long, title: String, intro: String, privacy: Int, cookie: String, csrf: String): Boolean = false
    suspend fun deleteFavFolder(mediaId: Long, cookie: String, csrf: String): Boolean = false
    suspend fun moveFavVideos(aids: List<Long>, fromFolder: Long, toFolder: Long, cookie: String, csrf: String): Boolean = false
}
