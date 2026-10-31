package com.qx.orbit.bili.presentation.util

import androidx.navigation.NavHostController
import com.qx.orbit.bili.data.cache.VideoDetailCache
import com.qx.orbit.bili.data.cache.VideoPreview
import com.qx.orbit.bili.data.model.VideoCard

/**
 * 打开视频详情页。
 *
 * 在跳转前先做两件事，让等待时间与导航动画、页面组合重叠：
 * 1. 登记点击处已知的标题/封面，详情页首帧直接画真内容而不是转圈
 * 2. 立刻发起详情请求（按 bvid 去重，详情页会复用它，不会重复发）
 */
fun NavHostController.navigateToVideoDetail(
    bvid: String,
    aid: Long,
    preview: VideoPreview? = null
) {
    if (preview != null) VideoDetailCache.putPreview(bvid, preview)
    VideoDetailCache.prefetch(bvid, aid)
    navigate("detail/$bvid/$aid")
}

/** 列表卡片自带的展示信息，用作详情页首帧骨架。 */
fun VideoCard.toPreview(): VideoPreview = VideoPreview(title = title, cover = cover, upName = upName)
