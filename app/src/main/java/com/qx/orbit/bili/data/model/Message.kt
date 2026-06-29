package com.qx.orbit.bili.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class MessageCard(
    val id: Long = 0,
    val user: List<UserInfo> = emptyList(),
    val timeStamp: Long = 0,
    val timeDesc: String = "",
    val content: String = "",
    val videoCard: VideoCard? = null,
    val replyInfo: Reply? = null,
    val subjectId: Long = 0,
    val businessId: Int = 0,
    val itemType: String = "",
    val getType: Int = 0,
    val sourceId: Long = 0,
    val rootId: Long = 0,
    val targetId: Long = 0
) {
    data class Cursor(
        val is_end: Boolean,
        val id: Long,
        val time: Long
    )

    companion object {
        const val GET_TYPE_REPLY = 0
        const val GET_TYPE_AT = 1
        const val GET_TYPE_LIKE = 2
    }
}

data class PrivateMessage(
    val content: JsonElement? = null,
    val type: Int = 0,
    val timestamp: Long = 0,
    val uid: Long = 0,
    val name: String = "",
    val msgId: Long = 0,
    val msgSeqno: Long = 0,
    val msg_source: Int = 0
) {
    companion object {
        const val TYPE_TEXT = 1
        const val TYPE_VIDEO = 7
        const val TYPE_PIC = 2
        const val TYPE_RETRACT = 5
        const val TYPE_FACE = 6
        const val TYPE_NOMAL_CARD = 10
        const val TYPE_PIC_CARD = 13
        const val TYPE_TEXT_WITH_VIDEO = 16
        const val TYPE_SYSTEM = 18
    }
}

data class PrivateMsgSession(
    val talkerUid: Long = 0,
    val unread: Int = 0,
    val contentType: Int = 0,
    val content: JsonElement? = null
)

data class MessageSettingItem(
    val key: String = "",
    val title: String = "",
    val desc: String = "",
    val type: Int = TYPE_SWITCH,
    val value: Boolean = false,
    val options: Array<String>? = null
) {
    companion object {
        const val TYPE_SWITCH = 0
        const val TYPE_CHOOSE = 1
    }
}
