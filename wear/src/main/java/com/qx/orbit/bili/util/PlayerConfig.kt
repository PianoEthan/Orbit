package com.qx.orbit.bili.util

object PlayerConfig {
    const val KEY_TRY_LOOK = "try_look_enable"
    
    const val DEFAULT_TRY_LOOK_ENABLE = true
    
    fun isTryLookEnabled(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_TRY_LOOK, DEFAULT_TRY_LOOK_ENABLE)
    }
    
    fun setTryLookEnabled(enabled: Boolean) {
        SharedPreferencesUtil.putBoolean(KEY_TRY_LOOK, enabled)
    }
}