package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserInfo(
    @SerializedName(value = "mid") val mid: Long = 0,
    @SerializedName(value = "name", alternate = ["uname"]) val name: String = "",
    @SerializedName(value = "face", alternate = ["avatar"]) val avatar: String = "",
    val sign: String = "",
    @SerializedName(value = "follower", alternate = ["fans"]) val fans: Int = 0,
    val level: Int = 0,
    val following: Int = 0,
    val followed: Boolean = false,
    val title: String = "",
    val notice: String = "",
    val official: Int = 0,
    val officialDesc: String = "",
    val mtime: Long = 0,
    val vip_role: Int = 0,
    val vip_nickname_color: String = "",
    val current_exp: Long = 0,
    val next_exp: Long = 0,
    val medal_name: String = "",
    val medal_level: Int = 0,
    val sys_notice: String = "",
    val live_room: LiveRoom? = null,
    val is_senior_member: Int = 0,
    val is_follow_display: Boolean = false
) : Parcelable
