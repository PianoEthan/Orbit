package com.qx.orbit.bili.core.api

data class CoreHotword(
    val keyword: String,
    val showName: String = keyword,
    val iconUrl: String = "",
)

data class CoreSearchUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String = "",
    val fans: Int = 0,
)

data class CoreSearchSeason(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val subtitle: String = "",
    val areas: String = "",
)

fun interface SearchGateway {
    suspend fun searchVideos(keyword: String, page: Int): List<CoreVideo>
    suspend fun loadHotwords(): List<CoreHotword> = emptyList()
    suspend fun loadSuggestions(keyword: String): List<String> = emptyList()
    suspend fun searchUsers(keyword: String, page: Int): List<CoreSearchUser> = error("暂不支持用户搜索")
    suspend fun searchRooms(keyword: String, page: Int): List<CoreLiveRoom> = error("暂不支持直播搜索")
    suspend fun searchSeasons(keyword: String, page: Int): List<CoreSearchSeason> = error("暂不支持番剧搜索")
}
