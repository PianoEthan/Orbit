package com.qx.orbit.bili.presentation.util

/**
 * 缓存默认目标分P：优先用观看历史里记录的那一页（与「播放」按钮挑 cid 的规则一致），
 * 没有历史或历史不属于当前视频（比如看过合集的别的集）时回落到 P1。
 */
internal fun currentCachePageIndex(cids: List<Long>, historyCid: Long?): Int {
    if (cids.isEmpty()) return 0
    val index = historyCid?.let { cids.indexOf(it) } ?: -1
    return if (index in cids.indices) index else 0
}
