package com.qx.orbit.bili.data.model

data class Announcement(
    val id: Int = 0,
    val ctime: String = "",
    val title: String = "",
    val content: String = ""
)

data class Tutorial(
    val name: String = "",
    val description: String = "",
    val imgid: String = "",
    val type: Int = 0,
    val content: List<CustomText> = emptyList()
)

data class CustomText(
    val type: Int = 0,
    val text: String = "",
    val style: String = "",
    val color: String = ""
)

data class SettingSection(
    val type: String = "",
    val id: String = "",
    val name: String = "",
    val desc: String = "",
    val defaultValue: String = ""
)

data class LocalVideo(
    val cover: String = "",
    val title: String = "",
    val pageList: List<String> = emptyList(),
    val videoFileList: List<String> = emptyList(),
    val danmakuFileList: List<String> = emptyList(),
    val sizeList: List<Long> = emptyList(),
    val size: Long = 0
)

data class DownloadSection(
    val id: Long = 0,
    val type: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val qn: Int = 0,
    val url_cover: String = "",
    val title: String = "",
    val child: String = "",
    val state: String = "",
    val downloadType: String = "",
    val audioUrl: String = ""
)

data class PageInfo(
    val page_num: Int = 0,
    val require_ps: Int = 0,
    val total: Int = 0,
    val return_ps: Int = 0
)

data class ApiResult(
    val code: Int = -1,
    val message: String = "",
    val offset: Long = 0,
    val timestamp: Long = 0,
    val business: String = "",
    val isBottom: Boolean = false
)
