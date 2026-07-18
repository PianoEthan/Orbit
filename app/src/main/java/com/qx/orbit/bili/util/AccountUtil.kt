package com.qx.orbit.bili.util

import com.qx.orbit.bili.data.api.UserInfoApi

object AccountUtil {
    suspend fun isUserLoggedIn(): Boolean {
        return try {
            val navInfo = UserInfoApi.getNavInfo()
            navInfo?.isLogin == true
        } catch (e: Exception) {
            false
        }
    }
}