package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ElectricApi {

    private val api by lazy { BiliApiService.create() }

    internal data class ElectricPanelData(
        @SerializedName("count") val count: Int = 0,
        @SerializedName("total_count") val total_count: Int = 0,
        @SerializedName("total") val total: Int = 0,
        @SerializedName("special_day") val special_day: Int = 0,
        @SerializedName("list") val list: List<ElectricUserItem>? = null
    )

    internal data class ElectricUserItem(
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("avatar") val avatar: String? = null,
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("pay_mid") val pay_mid: Long = 0,
        @SerializedName("rank") val rank: Int = 0,
        @SerializedName("trend_type") val trend_type: Int = 0,
        @SerializedName("message") val message: String? = null,
        @SerializedName("msg_hidden") val msg_hidden: Int = 0,
        @SerializedName("vip_info") val vip_info: VipInfo? = null
    )

    internal data class VipInfo(
        @SerializedName("vipDueMsec") val vipDueMsec: Long = 0,
        @SerializedName("vipStatus") val vipStatus: Int = 0,
        @SerializedName("vipType") val vipType: Int = 0
    )

    suspend fun getElectricPanel(upMid: Long): ElectricPanel? = withContext(Dispatchers.IO) {
        when (val resp = api.getElectricPanel(upMid)) {
            is com.qx.orbit.bili.data.remote.Result.Success -> {
                val type = object : TypeToken<ApiResponse<ElectricPanelData>>() {}.type
                val parsed: ApiResponse<ElectricPanelData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext null
                val data = parsed.data
                ElectricPanel(
                    count = data.count,
                    total_count = data.total_count,
                    total = data.total,
                    special_day = data.special_day,
                    list = data.list?.map {
                        ElectricUser(
                            uname = it.uname ?: "",
                            avatar = it.avatar ?: "",
                            mid = it.mid,
                            pay_mid = it.pay_mid,
                            rank = it.rank,
                            trend_type = it.trend_type,
                            message = it.message ?: "",
                            msg_hidden = it.msg_hidden,
                            vip_info = it.vip_info?.let { v ->
                                ElectricUser.VipInfoData(
                                    vipDueMsec = v.vipDueMsec,
                                    vipStatus = v.vipStatus,
                                    vipType = v.vipType
                                )
                            }
                        )
                    } ?: emptyList()
                )
            }
            is com.qx.orbit.bili.data.remote.Result.Error -> null
        }
    }
}
