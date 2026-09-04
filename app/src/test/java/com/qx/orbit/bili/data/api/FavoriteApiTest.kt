package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.remote.GsonConfig
import org.junit.Assert.*
import org.junit.Test

class FavoriteApiTest {
    @Test
    fun decodesDefaultAndPrivacyBitsIndependently() {
        for (attr in 0..3) {
            val item = GsonConfig.gson.fromJson(
                """{"id":12345,"title":"学习","intro":"技术视频","attr":$attr,"media_count":8}""",
                FavoriteApi.V3FavFolderItem::class.java
            ).toFolder()
            assertEquals(attr < 2, item.isDefault)
            assertEquals(attr == 1 || attr == 3, item.isPrivate)
            assertEquals(12345L, item.mediaId)
            assertEquals("技术视频", item.intro)
            assertEquals(8, item.videoCount)
        }
    }
}
