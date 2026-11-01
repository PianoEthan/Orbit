package com.qx.orbit.bili.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.qx.orbit.bili.data.model.InteractionVideoResult
import com.qx.orbit.bili.data.remote.BilibiliApiException
import com.qx.orbit.bili.data.remote.Result
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InteractionVideoApiTest {
    @Test
    fun parsesDocumentedEdgeWithoutConfusingEdgeAndCid() {
        val video = success(InteractionVideoApi.parseEdgeInfo(json(DOCUMENT_EDGE)))
        assertEquals("4", video.title)
        assertEquals(5556092L, video.edgeId)
        assertEquals(2, video.storyList.size)
        val current = video.storyList.last()
        assertEquals(5556092L, current.nodeId)
        assertEquals(5556092L, current.edgeId)
        assertEquals(125499378L, current.cid)
        assertEquals(82000L, current.startPos)
        assertEquals(15L, current.cursor)
        assertEquals(1, current.isCurrent)
        val edge = requireNotNull(video.edges)
        assertEquals(1920, edge.dimension?.width)
        assertEquals(1000, edge.dimension?.height)
        assertEquals(0, edge.dimension?.rotate)
        assertEquals("", edge.dimension?.sar)
        val question = edge.questions.single()
        assertEquals(2, question.type)
        assertEquals(300L, question.startTimeR)
        assertEquals(-1L, question.duration)
        assertEquals(1, question.pauseVideo)
        assertEquals(3, question.choices.size)
        val choice = question.choices.first()
        assertEquals(5556133L, choice.id)
        assertEquals(125499249L, choice.cid)
        assertEquals("JUMP 5556133 125499249", choice.platformAction)
        assertEquals("\$H7g_64_PG2EVS>=1.00 && \$H7g_64_PG2EVS<=80.00", choice.condition)
        assertEquals(947.0, requireNotNull(choice.x), 0.0)
        assertEquals(1, choice.isDefault)
        assertEquals("d8fbffff", edge.skin?.titleTextColor)
        assertEquals(1.0, requireNotNull(edge.skin).titleShadowOffsetY, 0.0)
        assertEquals(73267982L, video.preload?.video?.first()?.aid)
        assertEquals(125502707L, video.preload?.video?.last()?.cid)
        assertEquals(97.0, video.hiddenVars.first().value, 0.0)
        assertEquals("\$H7g_64_PG2EVS", video.hiddenVars.first().idV2)
        assertEquals(0, video.hiddenVars.first().skipOverwrite)
        assertEquals(0, video.isLeaf)
    }

    @Test
    fun retainsLongIdsFractionsUnknownTypesAndOpaqueAnimations() {
        val video = success(InteractionVideoApi.parseEdgeInfo(json("""
            {"code":0,"data":{
              "edge_id":9007199254740993,"unknown":{"future":true},
              "no_tutorial":1,"no_backtracking":2,"no_evaluation":3,
              "edges":{"questions":[{"id":4294967297,"type":255,"start_time_r":-123,
                "duration":12,"pause_video":9,"fade_in_time":100,"fade_out_time":200,
                "choices":[{"id":9007199254740995,"cid":9007199254740997,
                  "native_action":"${'$'}v+=0.25","x":1.5,"y":-2.25,"text_align":7,"is_hidden":1,
                  "selected":{"future":[1,true],"type":42},"submited":["opaque"]}]}]},
              "hidden_vars":[{"value":-1.75,"type":88,"skip_overwrite":7,"name":"score"}]
            }}
        """)))
        assertEquals(9007199254740993L, video.edgeId)
        val question = requireNotNull(video.edges).questions.single()
        assertEquals(4294967297L, question.id)
        assertEquals(255, question.type)
        assertEquals(-123L, question.startTimeR)
        assertEquals(100L, question.fadeInTime)
        assertEquals(200L, question.fadeOutTime)
        val choice = question.choices.single()
        assertEquals(9007199254740995L, choice.id)
        assertEquals(9007199254740997L, choice.cid)
        assertEquals("\$v+=0.25", choice.nativeAction)
        assertEquals(1.5, requireNotNull(choice.x), 0.0)
        assertEquals(-2.25, requireNotNull(choice.y), 0.0)
        assertEquals(7, choice.textAlign)
        assertEquals(1, choice.isHidden)
        assertEquals(json("{\"future\":[1,true],\"type\":42}"), choice.selected)
        assertEquals(json("[\"opaque\"]"), choice.submited)
        assertEquals(-1.75, video.hiddenVars.single().value, 0.0)
        assertEquals(88, video.hiddenVars.single().type)
        assertEquals(7, video.hiddenVars.single().skipOverwrite)
        assertEquals(1, video.noTutorial)
        assertEquals(2, video.noBacktracking)
        assertEquals(3, video.noEvaluation)
    }

    @Test
    fun missingNullAndEmptyOptionalFieldsHaveSafeDefaults() {
        for (data in listOf(
            "{\"edge_id\":1}",
            """{"edge_id":1,"is_leaf":1,"story_list":[],"hidden_vars":[],"preload":{"video":[]},"edges":{"questions":[]}}""",
            """{"edge_id":1,"story_list":null,"hidden_vars":null,"preload":null,"edges":null}"""
        )) {
            val video = success(InteractionVideoApi.parseEdgeInfo(json("{\"code\":0,\"data\":$data}")))
            assertTrue(video.storyList.isEmpty())
            assertTrue(video.hiddenVars.isEmpty())
            assertTrue(video.edges?.questions.orEmpty().isEmpty())
        }
        val video = success(InteractionVideoApi.parseEdgeInfo(json("""
            {"code":0,"data":{"edge_id":1,"edges":{"questions":[{}]},"hidden_vars":[{}]}}
        """)))
        val question = requireNotNull(video.edges).questions.single()
        assertEquals(-1L, question.duration)
        assertNull(question.startTime)
        assertNull(question.startTimeR)
        assertTrue(question.choices.isEmpty())
        assertNull(video.hiddenVars.single().skipOverwrite)
    }

    @Test
    fun malformedResponsesAreNotSuccessfulEmptyData() {
        for (response in listOf(
            "null", "[]", "{}", "{\"code\":0}", "{\"code\":0,\"data\":null}",
            """{"code":0,"data":{}}""",
            """{"code":0,"data":{"edge_id":null}}""",
            """{"code":0,"data":{"edge_id":0}}""",
            """{"code":0,"data":{"edge_id":-1}}""",
            """{"code":0,"data":{"edge_id":1,"edges":{"questions":[null]}}}""",
            """{"code":0,"data":{"edge_id":1.5}}""",
            """{"code":0,"data":{"edge_id":9223372036854775808}}""",
            """{"code":0,"data":{"edge_id":1,"edges":{"questions":{}}}}"""
        )) {
            assertTrue(response, InteractionVideoApi.parseEdgeInfo(json(response)) is InteractionVideoResult.InvalidResponse)
        }
    }

    @Test
    fun graphInvalidAuthApiAndVoucherAreDistinctFromSuccess() {
        val expired = InteractionVideoApi.parseEdgeInfo(json("""{"code":99003,"message":"expired"}"""))
        assertEquals(InteractionVideoResult.GraphInvalid("expired"), expired)
        for (code in listOf(-101, -400, -404, 99077, -352)) {
            val error = InteractionVideoApi.parseEdgeInfo(json("""{"code":$code,"message":"error"}"""))
            assertEquals(InteractionVideoResult.ApiError(code, "error"), error)
            assertEquals(error, PlayerApi.parseMetadata(json("""{"code":$code,"message":"error"}""")))
        }
        for (response in listOf(
            """{"code":0,"v_voucher":"root-voucher","data":{}}""",
            """{"code":0,"data":{"v_voucher":"data-voucher"}}""",
            """{"code":0,"v_voucher":"","data":{"v_voucher":"data-voucher"}}"""
        )) {
            assertTrue(InteractionVideoApi.parseEdgeInfo(json(response)) is InteractionVideoResult.VoucherRequired)
            assertTrue(PlayerApi.parseMetadata(json(response)) is InteractionVideoResult.VoucherRequired)
        }
    }

    @Test
    fun metadataDistinguishesOrdinaryVideoAndInvalidInteraction() {
        val ordinary = success(PlayerApi.parseMetadata(json("""
            {"code":0,"data":{"subtitle":{"subtitles":[]},"view_points":[]}}
        """)))
        assertNull(ordinary.interaction)
        assertNull(ordinary.history)
        assertTrue(ordinary.subtitleLinks.isEmpty())
        assertTrue(ordinary.viewPoints.isEmpty())
        for (interaction in listOf("null", "{}", "{\"graph_version\":0}", "{\"graph_version\":-1}", "[]")) {
            assertTrue(PlayerApi.parseMetadata(json("{\"code\":0,\"data\":{\"interaction\":$interaction}}"))
                is InteractionVideoResult.InvalidResponse)
        }
    }

    @Test
    fun metadataPreservesCurrentSubtitleViewPointAndHistoryConversions() {
        val metadata = success(PlayerApi.parseMetadata(json("""
            {"code":0,"data":{
              "interaction":{"graph_version":9007199254740993,"msg":"login hint",
                "error_toast":"剧情图被修改已失效","mark":7,"need_reload":2},
              "subtitle":{"subtitles":[
                {"id":4294967297,"lan":"zh-CN","lan_doc":"中文","subtitle_url":"//example/1","ai_status":2},
                {"id":2,"lan_doc":"AI 中文","ai_type":1},
                {"id":3,"lan":"en","type":1},
                {"id":4,"lan_doc":"人工","ai_status":0},
                {"id":5,"ai_status":1}
              ]},
              "view_points":[{"content":"chapter","from":10,"to":20,"type":3,"imgUrl":"img","logoUrl":"logo"}],
              "last_play_time":12345,"last_play_cid":4294967298
            }}
        """)))
        assertEquals(9007199254740993L, metadata.interaction?.graphVersion)
        assertEquals("login hint", metadata.interaction?.message)
        assertEquals("剧情图被修改已失效", metadata.interaction?.errorToast)
        assertEquals(7, metadata.interaction?.mark)
        assertEquals(2, metadata.interaction?.needReload)
        assertEquals(4294967297L, metadata.subtitleLinks.first().id)
        assertEquals(listOf("中文 (AI)", "AI 中文", "en (AI)", "人工", " (AI)"), metadata.subtitleLinks.map { it.lang })
        assertTrue(metadata.subtitleLinks.first().isAI)
        assertFalse(metadata.subtitleLinks[3].isAI)
        assertEquals("//example/1", metadata.subtitleLinks.first().url)
        val point = metadata.viewPoints.single()
        assertEquals("chapter", point.content)
        assertEquals(10, point.from)
        assertEquals(20, point.to)
        assertEquals(3, point.type)
        assertEquals("img", point.imgUrl)
        assertEquals("logo", point.logoUrl)
        assertEquals(4294967298L, metadata.history?.cid)
        assertEquals(12, metadata.history?.progress)
    }

    @Test
    fun injectedRequestsKeepParametersAndFetchMetadataOnlyOnce() = runBlocking {
        var edgeCalls = 0
        val result = InteractionVideoApi.getEdgeInfo(
            bvid = "BV1UE411y7Wy", graphVersion = 155446L,
            request = { aid, bvid, graphVersion, edgeId ->
                edgeCalls++
                assertNull(aid)
                assertEquals("BV1UE411y7Wy", bvid)
                assertEquals(155446L, graphVersion)
                assertNull(edgeId)
                Result.Success(json(DOCUMENT_EDGE))
            }
        )
        assertEquals(5556092L, success(result).edgeId)
        assertEquals(1, edgeCalls)
        InteractionVideoApi.getEdgeInfo(aid = 4294967297, graphVersion = 155446, edgeId = 5556092) { aid, bvid, graph, edge ->
            assertEquals(4294967297L, aid)
            assertNull(bvid)
            assertEquals(155446L, graph)
            assertEquals(5556092L, edge)
            Result.Success(json(DOCUMENT_EDGE))
        }
        var metadataCalls = 0
        val metadata = PlayerApi.getMetadata(10, 20) { aid, cid ->
            metadataCalls++
            assertEquals(10L, aid)
            assertEquals(20L, cid)
            Result.Success(json("""{"code":0,"data":{"interaction":{"graph_version":99}}}"""))
        }
        assertEquals(99L, success(metadata).interaction?.graphVersion)
        assertEquals(1, metadataCalls)
    }

    @Test
    fun resultErrorsAndThrownTransportFailuresRemainFailures() = runBlocking {
        for (error in listOf(
            BilibiliApiException(99003, "expired"),
            BilibiliApiException(-101, "login"),
            BilibiliApiException(-2, "offline"),
            BilibiliApiException(-1, "HTTP 503", 503)
        )) {
            val edge = InteractionVideoApi.getEdgeInfo(aid = 1, graphVersion = 2) { _, _, _, _ -> Result.Error(error) }
            val metadata = PlayerApi.getMetadata(1, 2) { _, _ -> Result.Error(error) }
            assertEquals(edge, metadata)
            when (error.code) {
                99003 -> assertTrue(edge is InteractionVideoResult.GraphInvalid)
                -101 -> assertEquals(InteractionVideoResult.ApiError(-101, "login"), edge)
                else -> assertSame(error, (edge as InteractionVideoResult.TransportError).cause)
            }
        }
        val failure = IOException("offline")
        val edge = InteractionVideoApi.getEdgeInfo(aid = 1, graphVersion = 2) { _, _, _, _ -> throw failure }
        val metadata = PlayerApi.getMetadata(1, 2) { _, _ -> throw failure }
        assertSame(failure, (edge as InteractionVideoResult.TransportError).cause)
        assertSame(failure, (metadata as InteractionVideoResult.TransportError).cause)
    }

    @Test
    fun cancellationIsRethrownByBothRequestApis() = runBlocking {
        val cancellation = CancellationException("cancelled")
        try {
            InteractionVideoApi.getEdgeInfo(aid = 1, graphVersion = 2) { _, _, _, _ -> throw cancellation }
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        try {
            PlayerApi.getMetadata(1, 2) { _, _ -> throw cancellation }
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun absoluteAndRelativeQuestionTimesRemainIndependent() {
        val video = success(InteractionVideoApi.parseEdgeInfo(json("""
            {"code":0,"data":{"edge_id":1,"edges":{"questions":[
              {"type":4,"start_time":22491,"start_time_r":6000,"duration":2000},
              {"type":4,"start_time":0,"start_time_r":null},
              {"type":127,"start_time":null,"start_time_r":300}
            ]}}}
        """)))
        val questions = requireNotNull(video.edges).questions
        assertEquals(22491L, questions[0].startTime)
        assertEquals(6000L, questions[0].startTimeR)
        assertEquals(0L, questions[1].startTime)
        assertNull(questions[1].startTimeR)
        assertEquals(127, questions[2].type)
        assertNull(questions[2].startTime)
        assertEquals(300L, questions[2].startTimeR)
    }

    @Test
    fun interactionHistoryNodeIsOpaqueAndReloadIsOptional() {
        for (historyNode in listOf(
            """{"cid":245682070,"node_id":1,"future":[true]}""",
            """[1,"unknown"]""", "42", "null"
        )) {
            val metadata = success(PlayerApi.parseMetadata(json("""
                {"code":0,"data":{"interaction":{"graph_version":1762245,"history_node":$historyNode}}}
            """)))
            val interaction = requireNotNull(metadata.interaction)
            assertEquals(json(historyNode).takeUnless { it.isJsonNull }, interaction.historyNode)
            assertNull(interaction.needReload)
        }
        val metadata = success(PlayerApi.parseMetadata(json("""
            {"code":0,"data":{"interaction":{"graph_version":1762245,"need_reload":0}}}
        """)))
        assertEquals(0, metadata.interaction?.needReload)
        assertNull(metadata.interaction?.historyNode)
    }

    private fun json(value: String): JsonElement = JsonParser.parseString(value)

    private fun <T> success(result: InteractionVideoResult<T>): T {
        assertTrue("Expected success, got $result", result is InteractionVideoResult.Success)
        return (result as InteractionVideoResult.Success).data
    }

    private companion object {
        val DOCUMENT_EDGE = """
            {"code":0,"message":"0","ttl":1,"data":{
              "title":"4","edge_id":5556092,
              "story_list":[
                {"node_id":1,"edge_id":1,"title":"0 片头","cid":127486603,"start_pos":27000,
                  "cover":"http://i0.hdslb.com/bfs/steins-gate/127486603_screenshot.jpg","cursor":0},
                {"node_id":5556092,"edge_id":5556092,"title":"4","cid":125499378,"start_pos":82000,
                  "cover":"http://i0.hdslb.com/bfs/steins-gate/125499378_screenshot.jpg","is_current":1,"cursor":15}
              ],
              "edges":{
                "dimension":{"width":1920,"height":1000,"rotate":0,"sar":""},
                "questions":[{"id":0,"type":2,"start_time_r":300,"duration":-1,"pause_video":1,"title":"",
                  "choices":[
                    {"id":5556133,"platform_action":"JUMP 5556133 125499249","native_action":"",
                      "condition":"${'$'}H7g_64_PG2EVS>=1.00 && ${'$'}H7g_64_PG2EVS<=80.00","cid":125499249,
                      "x":947,"y":499,"text_align":2,"option":"A <你已成为杀手> 试图砍死萌新 (成功率20%)","is_default":1},
                    {"id":5556134,"platform_action":"JUMP 5556134 125502707","native_action":"",
                      "condition":"${'$'}H7g_64_PG2EVS>=81.00 && ${'$'}H7g_64_PG2EVS<=100.00","cid":125502707,
                      "x":949,"y":502,"text_align":2,"option":"B <你已成为杀手> 试图砍死萌新 (成功率20%)"},
                    {"id":5556135,"platform_action":"JUMP 5556135 125499249","native_action":"","condition":"",
                      "cid":125499249,"x":120,"y":145,"text_align":2,"option":"C 直接【砍杀失败】"}
                  ]}],
                "skin":{"choice_image":"https://i0.hdslb.com/bfs/app/db0ae7700d4fb1416c8b305bcfb6f0948f818cc9.png",
                  "title_text_color":"d8fbffff","title_shadow_color":"00000033","title_shadow_offset_y":1,
                  "title_shadow_radius":1,"progressbar_color":"ffffffff","progressbar_shadow_color":"000000cc"}
              },
              "preload":{"video":[{"aid":73267982,"cid":125499249},{"aid":73267982,"cid":125502707}]},
              "hidden_vars":[
                {"value":97,"id":"v-H7g@PG2EVS","id_v2":"${'$'}H7g_64_PG2EVS","type":2,"is_show":0,"name":"随机值","skip_overwrite":0},
                {"value":0,"id":"v-YWB6dk1oCP","id_v2":"${'$'}YWB6dk1oCP","type":1,"is_show":1,"name":"达成假结局次数","skip_overwrite":0},
                {"value":0,"id":"v-Zh4JACIiId","id_v2":"${'$'}Zh4JACIiId","type":1,"is_show":1,"name":"死亡次数","skip_overwrite":0},
                {"value":0,"id":"v-a2vplaQlsP","id_v2":"${'$'}a2vplaQlsP","type":1,"is_show":1,"name":"达成真结局次数","skip_overwrite":0},
                {"value":1,"id":"v-lMQqQ994Sk","id_v2":"${'$'}lMQqQ994Sk","type":1,"is_show":1,"name":"循环编号","skip_overwrite":0}
              ],"is_leaf":0
            }}
        """
    }
}
