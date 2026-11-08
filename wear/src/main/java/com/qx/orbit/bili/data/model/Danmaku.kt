package com.qx.orbit.bili.data.model

data class DanmakuElem(
    @JvmField val id: Long = 0,
    @JvmField val progress: Int = 0,
    @JvmField val mode: Int = 1,
    @JvmField val fontsize: Int = 25,
    @JvmField val color: Int = 0,
    @JvmField val midHash: String = "",
    @JvmField val content: String = "",
    @JvmField val ctime: Long = 0,
    @JvmField val weight: Int = 0,
    @JvmField val action: String = "",
    @JvmField val pool: Int = 0,
    @JvmField val idStr: String = "",
    @JvmField val attr: Int = 0,
    @JvmField val animation: String = ""
)

data class DmSegMobileReply(
    @JvmField val elems: List<DanmakuElem> = emptyList()
)
