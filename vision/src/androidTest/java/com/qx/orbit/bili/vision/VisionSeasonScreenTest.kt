package com.qx.orbit.bili.vision

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.qx.orbit.bili.core.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisionSeasonScreenTest {
    @get:Rule val compose = createComposeRule()
    private val season = CoreSeason(42, 43, "番剧测试", "", "全两集", "会员")
    private val video = CoreVideo(2, "BV2", "第二集", "", "番剧测试", cid = 102, episodeId = 12, seasonId = 42, startPositionMs = 25000)
    private val gateway = object : SeasonGateway {
        override suspend fun loadCatalog(type: Int, page: Int, cookie: String) = CoreSeasonPage(listOf(season.copy(title = if (type == 2) "电影测试" else "番剧测试")), false)
        override suspend fun loadFollowed(mid: Long, type: Int, page: Int, cookie: String) = CoreSeasonPage(emptyList(), false)
        override suspend fun loadSeason(seasonId: Long, cookie: String) = CoreSeasonDetail(season, "剧集简介", listOf(CoreSeasonEpisode(12, "第二集", "会员", video)),
            listOf(season, season.copy(id = 44, title = "第二季")), false, 12, 25000)
        override suspend fun follow(seasonId: Long, followed: Boolean, cookie: String, csrf: String) = error("No real account writes")
    }

    @Test fun catalogSwitchesCategoryAndOpensSeason() {
        var opened = 0L
        compose.setContent {
            val model = remember { VisionSeasonViewModel(gateway) }
            VisionTheme { VisionSeasonCatalogScreen({}, { opened = it }, {}, model) }
        }
        compose.onNodeWithText("番剧测试").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(42L, opened) }
        compose.onNodeWithText("电影").performClick()
        compose.onNodeWithText("电影测试").assertIsDisplayed()
    }

    @Test fun detailResumePreservesEpisodeAndSeasonAndCanSwitchSeason() {
        var played: CoreVideo? = null
        var nextSeason = 0L
        compose.setContent {
            val model = remember { VisionSeasonViewModel(gateway) }
            VisionTheme { VisionSeasonDetailScreen(42, {}, { played = it }, { nextSeason = it }, {}, model) }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("继续观看"))
        compose.onNodeWithText("继续观看").performClick()
        compose.runOnIdle { assertEquals(video, played) }
        list.performScrollToNode(hasText("第二季"))
        compose.onNodeWithText("第二季").performClick()
        compose.runOnIdle { assertEquals(44L, nextSeason) }
        list.performScrollToNode(hasText("第二集 · 会员"))
        compose.onNodeWithText("第二集 · 会员").performClick()
        compose.runOnIdle { assertEquals(12L, played?.episodeId); assertEquals(42L, played?.seasonId) }
    }
}
