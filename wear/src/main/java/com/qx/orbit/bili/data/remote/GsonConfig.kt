package com.qx.orbit.bili.data.remote

import com.google.gson.*
import java.lang.reflect.Type

object GsonConfig {
    val gson: Gson = GsonBuilder()
        .setLenient()
        .serializeNulls()
        .registerTypeAdapter(Int::class.java, IntOrStringAdapter())
        .registerTypeAdapter(Int::class.javaObjectType, IntOrStringAdapter())
        .registerTypeAdapter(Long::class.java, LongOrStringAdapter())
        .registerTypeAdapter(Long::class.javaObjectType, LongOrStringAdapter())
        .create()

    private class IntOrStringAdapter : JsonDeserializer<Int>, JsonSerializer<Int> {
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Int {
            if (json == null || json.isJsonNull) return 0
            val prim = json.asJsonPrimitive
            return when {
                prim.isNumber -> prim.asInt
                prim.isString -> prim.asString.toIntOrNull() ?: 0
                prim.asBoolean -> 1
                else -> 0
            }
        }

        override fun serialize(src: Int?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src ?: 0)
        }
    }

    private class LongOrStringAdapter : JsonDeserializer<Long>, JsonSerializer<Long> {
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Long {
            if (json == null || json.isJsonNull) return 0L
            val prim = json.asJsonPrimitive
            return when {
                prim.isNumber -> prim.asLong
                prim.isString -> prim.asString.toLongOrNull() ?: 0L
                prim.asBoolean -> 1L
                else -> 0L
            }
        }

        override fun serialize(src: Long?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src ?: 0L)
        }
    }
}
