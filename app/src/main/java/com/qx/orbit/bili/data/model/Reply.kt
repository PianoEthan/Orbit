package com.qx.orbit.bili.data.model
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class ContentType(val typeCode: Int) {
    Video(1), Topic(2), Activity(4), ShortVideo(5), BlackRoom(6),
    Announcement(7), Live(8), ActivityContent(9), LiveAnnouncement(10),
    Photo(11), Article(12), Ticket(13), Audio(14), Judgement(15),
    Review(16), Dynamic(17), VideoPlaylist(18), AudioPlaylist(19),
    Manga_1(20), Manga_2(21), Manga_3(22), Opus(23), Course(33);

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.typeCode == code } ?: Video
    }
}

@Parcelize
data class Reply(
    val rpid: Long = 0,
    val oid: Long = 0,
    val root: Long = 0,
    val parent: Long = 0,
    val forceDelete: Boolean = false,
    val ofBvid: String = "",
    val pubTime: String = "",
    val sender: UserInfo? = null,
    val message: String = "",
    val pictureList: List<String> = emptyList(),
    val likeCount: Int = 0,
    val upLiked: Boolean = false,
    val upReplied: Boolean = false,
    val liked: Boolean = false,
    val childCount: Int = 0,
    val isDynamic: Boolean = false,
    val childMsgList: List<Reply> = emptyList(),
    val isTop: Boolean = false,
    val canPin: Boolean = false,
    val isUp: Boolean = false,
    val emotes: Map<String, Emote> = emptyMap(),
    val members: Map<String, Long> = emptyMap()
) : Parcelable

fun List<Reply>.withReplyTopState(rpid: Long, isTop: Boolean): List<Reply> {
    val updated = map { reply ->
        when {
            reply.rpid == rpid -> reply.copy(isTop = isTop)
            isTop && reply.isTop -> reply.copy(isTop = false)
            else -> reply
        }
    }
    return if (isTop) updated.sortedByDescending { it.rpid == rpid } else updated
}
