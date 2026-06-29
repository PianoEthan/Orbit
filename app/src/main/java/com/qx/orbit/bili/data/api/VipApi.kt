package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.*
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VipApi {

    private val api by lazy { BiliApiService.create() }

    internal data class VipPrivilegeData(
        @SerializedName("type") val type: Int = 0,
        @SerializedName("state") val state: Int = 0,
        @SerializedName("expire_time") val expire_time: Long = 0
    )

    internal data class VipPrivilegeListData(
        @SerializedName("list") val list: List<VipPrivilegeData>? = null,
        @SerializedName("vip_type") val vip_type: Int = 0,
        @SerializedName("vip_status") val vip_status: Int = 0,
        @SerializedName("vip_due_date") val vip_due_date: Long = 0
    )

    suspend fun getVipInfo(): VipDetailInfo? = withContext(Dispatchers.IO) {
        when (val resp = api.getVipInfo()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<VipPrivilegeListData>>() {}.type
                val apiResp: ApiResponse<VipPrivilegeListData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data ?: return@withContext null
                val privileges = data.list?.map {
                    VipDetailInfo.Privilege(type = it.type, state = it.state, expireTime = it.expire_time)
                } ?: emptyList()
                VipDetailInfo(
                    isVip = data.vip_status > 0,
                    level = data.vip_type,
                    vipStatus = data.vip_status,
                    vipType = data.vip_type,
                    vipDueDate = data.vip_due_date,
                    privilegeList = privileges
                )
            }
            is Result.Error -> null
        }
    }

    suspend fun addExperience(): Int = withContext(Dispatchers.IO) {
        when (val resp = api.addVipExperience(CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }
}
