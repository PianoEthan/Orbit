package com.qx.orbit.bili.data.model

data class ElectricPanel(
    val count: Int = 0,
    val total_count: Int = 0,
    val total: Int = 0,
    val special_day: Int = 0,
    val list: List<ElectricUser> = emptyList()
)

data class ElectricUser(
    val uname: String = "",
    val avatar: String = "",
    val mid: Long = 0,
    val pay_mid: Long = 0,
    val rank: Int = 0,
    val trend_type: Int = 0,
    val message: String = "",
    val msg_hidden: Int = 0,
    val vip_info: VipInfoData? = null
) {
    data class VipInfoData(
        val vipDueMsec: Long = 0,
        val vipStatus: Int = 0,
        val vipType: Int = 0
    )
}
