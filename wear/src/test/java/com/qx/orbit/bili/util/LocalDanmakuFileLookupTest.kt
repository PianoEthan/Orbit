package com.qx.orbit.bili.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 离线弹幕定位：`VideoDownloadManager.localDanmakuFileFor` 只查现成文件，不发网络请求。
 */
class LocalDanmakuFileLookupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun validXml(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<i>\n")
        append("<chatserver>chat.bilibili.com</chatserver>\n")
        append("<chatid>123</chatid>\n")
        append("<d p=\"1.0,1,25,16777215,1700000000,0,abcdef,1\">离线弹幕测试</d>\n")
        append("</i>")
    }

    @Test
    fun findsCachedXmlNextToVideo() {
        val video = temp.newFile("测试视频.mp4")
        val danmaku = File("${video.absolutePath}.danmaku.xml").apply { writeText(validXml()) }

        assertEquals(danmaku, VideoDownloadManager.localDanmakuFileFor(video.absolutePath))
    }

    @Test
    fun fallsBackToLegacyShortFileName() {
        val video = temp.newFile("标题.m4s")
        val legacy = File(temp.root, "标题.danmaku.xml").apply { writeText(validXml()) }

        assertEquals(legacy, VideoDownloadManager.localDanmakuFileFor(video.absolutePath))
    }

    @Test
    fun ignoresEmptyOrTruncatedCache() {
        val video = temp.newFile("空弹幕.mp4")
        File("${video.absolutePath}.danmaku.xml").apply { writeText("") }

        assertNull(VideoDownloadManager.localDanmakuFileFor(video.absolutePath))
    }

    @Test
    fun rejectsNonFilePathVideoSources() {
        assertNull(VideoDownloadManager.localDanmakuFileFor(""))
        assertNull(VideoDownloadManager.localDanmakuFileFor(null))
        assertNull(VideoDownloadManager.localDanmakuFileFor("content://media/external/video/media/42"))
        assertNull(VideoDownloadManager.localDanmakuFileFor("http://example.com/a.mp4"))
    }
}
