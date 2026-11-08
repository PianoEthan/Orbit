package com.qx.orbit.bili.data.api

import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.qx.orbit.bili.data.model.ApiResponse
import com.qx.orbit.bili.data.remote.GsonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EmoteApi {
    const val BUSINESS_REPLY = "reply"
    const val BUSINESS_DYNAMIC = "dynamic"

    private val api by lazy { BiliApiService.create() }

    data class Emote(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("package_id") val packageId: Int = 0,
        @SerializedName("text") val name: String = "",
        @SerializedName("url") val url: String = "",
        @SerializedName("emoticon_unique") val emoticonUnique: String = "",
        @SerializedName("meta") val meta: EmoteMeta? = null
    ) {
        data class EmoteMeta(
            @SerializedName("size") val size: Int = 1,
            @SerializedName("alias") val alias: String? = null
        )
    }

    data class EmotePackage(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("text") val text: String = "",
        @SerializedName("url") val url: String = "",
        @SerializedName("type") val type: Int = 0,
        @SerializedName("attr") val attr: Int = 0,
        @SerializedName("emote") val emotes: List<Emote> = emptyList(),
        @SerializedName("meta") val meta: PackageMeta? = null,
        @SerializedName("flags") val flags: PackageFlags? = null
    ) {
        data class PackageMeta(
            @SerializedName("size") val size: Int = 1,
            @SerializedName("item_id") val itemId: Int = -1
        )
        data class PackageFlags(
            @SerializedName("permanent") val permanent: Boolean = false
        )
    }

    data class EmotePanelData(
        @SerializedName("packages") val packages: List<EmotePackage>? = null
    )

    suspend fun getEmotes(business: String): List<EmotePackage> = withContext(Dispatchers.IO) {
        when (val resp = api.getEmotes(business)) {
            is com.qx.orbit.bili.data.remote.Result.Success -> {
                val type = TypeToken.getParameterized(ApiResponse::class.java, EmotePanelData::class.java).type
                val parsed: ApiResponse<EmotePanelData>? = GsonConfig.gson.fromJson(resp.data, type)
                parsed?.data?.packages ?: emptyList()
            }
            is com.qx.orbit.bili.data.remote.Result.Error -> emptyList()
        }
    }
}
