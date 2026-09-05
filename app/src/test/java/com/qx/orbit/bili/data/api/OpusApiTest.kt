package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.remote.GsonConfig
import org.junit.Assert.*
import org.junit.Test

class OpusApiTest {
    @Test
    fun readsFavoriteStateFromOpusModuleArray() {
        val item = GsonConfig.gson.fromJson("""
            {"id_str":"1056353752004427792","type":2,"modules":[
                {"module_type":"MODULE_TYPE_STAT","module_stat":{
                    "favorite":{"count":4,"status":true,"forbidden":false},
                    "like":{"count":17,"status":false},"comment":{"count":3}
                }}
            ]}
        """.trimIndent(), OpusApi.OpusRawItem::class.java)
        val opus = OpusApi.parseOpusFromHtml(item, 1056353752004427792L)
        assertEquals(1056353752004427792L, opus.parsedId)
        assertEquals(4, opus.stats?.favorite)
        assertEquals(true, opus.stats?.favoured)
        assertEquals(false, opus.stats?.fav_disabled)
        assertEquals(17, opus.stats?.like)
    }

    @Test
    fun disablesFavoriteForMissingOrForbiddenAction() {
        for (stat in listOf("{}", """{"favorite":{"count":0,"status":false,"forbidden":true}}""")) {
            val item = GsonConfig.gson.fromJson(
                """{"type":1,"modules":{"module_stat":$stat}}""",
                OpusApi.OpusRawItem::class.java
            )
            assertEquals(true, OpusApi.parseOpusFromHtml(item, 123L).stats?.fav_disabled)
        }
    }
}
