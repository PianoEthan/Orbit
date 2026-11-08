package com.qx.orbit.bili.data.model

data class LoginRecord(
    val mid: Long = 0,
    val deviceName: String = "",
    val loginType: String = "",
    val loginTime: String = "",
    val location: String = "",
    val ip: String = ""
)

data class ExpLog(
    val delta: Int = 0,
    val time: String = "",
    val reason: String = ""
)

data class CoinLog(
    val time: String = "",
    val delta: Int = 0,
    val reason: String = ""
)

data class MedalInfo(
    val target_id: Long = 0,
    val level: Int = 0,
    val medal_name: String = "",
    val medal_color_start: Int = 0,
    val medal_color_end: Int = 0,
    val medal_color_border: Int = 0,
    val guard_level: Int = 0,
    val wearing_status: Int = 0,
    val medal_id: Long = 0,
    val target_name: String = "",
    val target_icon: String = ""
)

data class FollowTag(
    val tagid: Int = 0,
    val name: String = "",
    val count: Int = 0
)

data class VipDetailInfo(
    val isVip: Boolean = false,
    val level: Int = 0,
    val vipStatus: Int = 0,
    val vipType: Int = 0,
    val vipDueDate: Long = 0,
    val privilegeList: List<Privilege> = emptyList()
) {
    data class Privilege(
        val type: Int = 0,
        val state: Int = 0,
        val expireTime: Long = 0
    )
}
