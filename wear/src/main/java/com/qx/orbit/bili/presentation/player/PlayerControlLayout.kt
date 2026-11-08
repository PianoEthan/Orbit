package com.qx.orbit.bili.presentation.player

import com.qx.orbit.bili.util.SharedPreferencesUtil

internal const val PLAYER_ACTION_NONE = 0
internal const val PLAYER_ACTION_DANMAKU = 1
internal const val PLAYER_ACTION_SPEED = 2
internal const val PLAYER_ACTION_VOLUME = 3
internal const val PLAYER_ACTION_SUBTITLE = 4
internal const val PLAYER_ACTION_ROTATE = 5
internal const val PLAYER_ACTION_SCALE = 6

internal data class PlayerControlLayout(
    val leftTop: Int,
    val leftBottom: Int,
    val rightTop: Int,
    val rightBottom: Int
)

internal fun loadPlayerControlLayout(): PlayerControlLayout {
    return PlayerControlLayout(
        leftTop = SharedPreferencesUtil.getInt(PLAYER_LEFT_TOP_KEY, PLAYER_ACTION_SCALE),
        leftBottom = SharedPreferencesUtil.getInt(PLAYER_LEFT_BOTTOM_KEY, PLAYER_ACTION_ROTATE),
        rightTop = SharedPreferencesUtil.getInt(PLAYER_RIGHT_TOP_KEY, PLAYER_ACTION_SPEED),
        rightBottom = SharedPreferencesUtil.getInt(PLAYER_RIGHT_BOTTOM_KEY, PLAYER_ACTION_DANMAKU)
    )
}

internal const val PLAYER_LEFT_TOP_KEY = "player_custom_btn_left"
internal const val PLAYER_LEFT_BOTTOM_KEY = "player_custom_btn_left_bottom"
internal const val PLAYER_RIGHT_TOP_KEY = "player_custom_btn_right"
internal const val PLAYER_RIGHT_BOTTOM_KEY = "player_custom_btn_right_bottom"
