package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FollowApi {

    private val api by lazy { BiliApiService.create() }

    internal data class FollowListData(
        @SerializedName("list") val list: List<FollowItem>? = null,
        @SerializedName("total") val total: Int = 0
    )

    internal data class FollowItem(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("face") val face: String? = null,
        @SerializedName("sign") val sign: String? = null,
        @SerializedName("official_verify") val official_verify: UserInfoApi.OfficialInfo? = null,
        @SerializedName("vip") val vip: UserInfoApi.VipInfoData? = null,
        @SerializedName("follower") val follower: Int = 0
    )

    internal data class TagItem(
        @SerializedName("tagid") val tagid: Int = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("count") val count: Int = 0
    )

    suspend fun getFollowingList(mid: Long, page: Int): Pair<Int, List<UserInfo>> = withContext(Dispatchers.IO) {
        when (val resp = api.getFollowingList(mid, page)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<FollowListData>>() {}.type
                val parsed: ApiResponse<FollowListData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess) return@withContext Pair(-1, emptyList<UserInfo>())
                val data = parsed.data ?: return@withContext Pair(1, emptyList<UserInfo>())
                val list = data.list
                if (list.isNullOrEmpty()) return@withContext Pair(1, emptyList<UserInfo>())
                Pair(0, list.map { it.toUserInfo() })
            }
            is Result.Error -> Pair(-1, emptyList())
        }
    }

    suspend fun getFollowerList(mid: Long, page: Int): Pair<Int, List<UserInfo>> = withContext(Dispatchers.IO) {
        when (val resp = api.getFollowerList(mid, page)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<FollowListData>>() {}.type
                val parsed: ApiResponse<FollowListData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess) return@withContext Pair(-1, emptyList<UserInfo>())
                val data = parsed.data ?: return@withContext Pair(1, emptyList<UserInfo>())
                val list = data.list
                if (list.isNullOrEmpty()) return@withContext Pair(1, emptyList<UserInfo>())
                Pair(0, list.map { it.toUserInfo() })
            }
            is Result.Error -> Pair(-1, emptyList())
        }
    }

    suspend fun getFollowTags(): List<FollowTag> = withContext(Dispatchers.IO) {
        when (val resp = api.getFollowTags()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<List<TagItem>>>() {}.type
                val parsed: ApiResponse<List<TagItem>>? = GsonConfig.gson.fromJson(resp.data, type)
                parsed?.data?.filterNotNull()?.map { item ->
                    FollowTag(
                        tagid = item.tagid,
                        name = item.name ?: "",
                        count = item.count
                    )
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun getFollowTagUsers(tagid: Int, page: Int): Pair<Int, List<UserInfo>> = withContext(Dispatchers.IO) {
        when (val resp = api.getFollowTagUsers(tagid, page)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<List<FollowItem>>>() {}.type
                val parsed: ApiResponse<List<FollowItem>>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess) return@withContext Pair(-1, emptyList<UserInfo>())
                val list = parsed.data
                if (list.isNullOrEmpty()) return@withContext Pair(1, emptyList<UserInfo>())
                Pair(0, list.filterNotNull().map { it.toUserInfo() })
            }
            is Result.Error -> Pair(-1, emptyList())
        }
    }

    private fun FollowItem.toUserInfo(): UserInfo = UserInfo(
        mid = mid,
        name = uname ?: "",
        avatar = face ?: "",
        sign = sign ?: "",
        fans = follower,
        official = official_verify?.type ?: -1,
        officialDesc = official_verify?.desc ?: "",
        vip_role = vip?.vipStatus ?: 0,
        vip_nickname_color = vip?.nickname_color ?: ""
    )
}
