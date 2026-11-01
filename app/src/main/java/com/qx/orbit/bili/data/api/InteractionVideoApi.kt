package com.qx.orbit.bili.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.qx.orbit.bili.data.model.InteractionChoice
import com.qx.orbit.bili.data.model.InteractionDimension
import com.qx.orbit.bili.data.model.InteractionEdge
import com.qx.orbit.bili.data.model.InteractionHiddenVar
import com.qx.orbit.bili.data.model.InteractionPreload
import com.qx.orbit.bili.data.model.InteractionPreloadVideo
import com.qx.orbit.bili.data.model.InteractionQuestion
import com.qx.orbit.bili.data.model.InteractionSkin
import com.qx.orbit.bili.data.model.InteractionStoryNode
import com.qx.orbit.bili.data.model.InteractionVideo
import com.qx.orbit.bili.data.model.InteractionVideoResult
import com.qx.orbit.bili.data.remote.BilibiliApiException
import com.qx.orbit.bili.data.remote.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object InteractionVideoApi {
    private val api by lazy { BiliApiService.create() }

    suspend fun getEdgeInfo(
        aid: Long? = null,
        bvid: String? = null,
        graphVersion: Long,
        edgeId: Long? = null,
        request: suspend (Long?, String?, Long, Long?) -> Result<JsonElement> = { av, bv, graph, edge ->
            api.getInteractionEdgeInfo(av, bv, graph, edge)
        }
    ): InteractionVideoResult<InteractionVideo> {
        require((aid != null && aid > 0) || !bvid.isNullOrBlank()) { "aid or bvid is required" }
        require(graphVersion > 0) { "graphVersion must be positive" }
        require(edgeId == null || edgeId >= 0) { "edgeId must not be negative" }
        return requestInteractionResponse(
            request = { request(aid?.takeIf { it > 0 }, bvid?.takeIf { it.isNotBlank() }, graphVersion, edgeId) },
            parse = ::parseEdgeInfo
        )
    }

    fun parseEdgeInfo(response: JsonElement): InteractionVideoResult<InteractionVideo> =
        parseInteractionResponse(response) { data ->
            val edgeId = requireNotNull(data.optionalLong("edge_id")) { "Missing edge_id" }
            require(edgeId > 0) { "Invalid edge_id" }
            InteractionVideo(
                title = data.string("title"),
                edgeId = edgeId,
                storyList = data.objects("story_list").map { story ->
                    InteractionStoryNode(
                        nodeId = story.long("node_id"), edgeId = story.long("edge_id"),
                        title = story.string("title"), cid = story.long("cid"),
                        startPos = story.long("start_pos"), cover = story.string("cover"),
                        isCurrent = story.int("is_current"), cursor = story.long("cursor")
                    )
                },
                edges = data.obj("edges")?.let { edge ->
                    InteractionEdge(
                        dimension = edge.obj("dimension")?.let { dimension ->
                            InteractionDimension(
                                width = dimension.int("width"), height = dimension.int("height"),
                                rotate = dimension.int("rotate"), sar = dimension.string("sar")
                            )
                        },
                        questions = edge.objects("questions").map(::parseQuestion),
                        skin = edge.obj("skin")?.let { skin ->
                            InteractionSkin(
                                choiceImage = skin.string("choice_image"),
                                titleTextColor = skin.string("title_text_color"),
                                titleShadowColor = skin.string("title_shadow_color"),
                                titleShadowOffsetX = skin.double("title_shadow_offset_x"),
                                titleShadowOffsetY = skin.double("title_shadow_offset_y"),
                                titleShadowRadius = skin.double("title_shadow_radius"),
                                progressbarColor = skin.string("progressbar_color"),
                                progressbarShadowColor = skin.string("progressbar_shadow_color")
                            )
                        }
                    )
                },
                preload = data.obj("preload")?.let { preload ->
                    InteractionPreload(preload.objects("video").map { video ->
                        InteractionPreloadVideo(aid = video.long("aid"), cid = video.long("cid"))
                    })
                },
                hiddenVars = data.objects("hidden_vars").map { variable ->
                    InteractionHiddenVar(
                        value = variable.double("value"), id = variable.string("id"),
                        idV2 = variable.string("id_v2"), type = variable.int("type"),
                        isShow = variable.int("is_show"), name = variable.string("name"),
                        skipOverwrite = variable.optionalInt("skip_overwrite")
                    )
                },
                isLeaf = data.int("is_leaf"), noTutorial = data.int("no_tutorial"),
                noBacktracking = data.int("no_backtracking"), noEvaluation = data.int("no_evaluation")
            )
        }

    private fun parseQuestion(question: InteractionJson) = InteractionQuestion(
        id = question.long("id"), type = question.int("type"),
        startTime = question.optionalLong("start_time"),
        startTimeR = question.optionalLong("start_time_r"), duration = question.long("duration", -1),
        pauseVideo = question.int("pause_video"), title = question.string("title"),
        choices = question.objects("choices").map { choice ->
            InteractionChoice(
                id = choice.long("id"), platformAction = choice.string("platform_action"),
                nativeAction = choice.string("native_action"), condition = choice.string("condition"),
                cid = choice.long("cid"), x = choice.optionalDouble("x"), y = choice.optionalDouble("y"),
                textAlign = choice.int("text_align"), option = choice.string("option"),
                selected = choice.raw("selected")?.deepCopy(), submited = choice.raw("submited")?.deepCopy(),
                isDefault = choice.int("is_default"), isHidden = choice.int("is_hidden")
            )
        },
        fadeInTime = question.long("fade_in_time"), fadeOutTime = question.long("fade_out_time")
    )
}

internal suspend fun <T> requestInteractionResponse(
    request: suspend () -> Result<JsonElement>,
    parse: (JsonElement) -> InteractionVideoResult<T>
): InteractionVideoResult<T> {
    return try {
        currentCoroutineContext().ensureActive()
        val response = request()
        currentCoroutineContext().ensureActive()
        when (response) {
            is Result.Success -> parse(response.data)
            is Result.Error -> interactionError(response.exception)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        InteractionVideoResult.TransportError(exception)
    }
}

private fun interactionError(error: BilibiliApiException): InteractionVideoResult<Nothing> = when {
    error.code == 99003 -> InteractionVideoResult.GraphInvalid(error.message.orEmpty())
    error.code == -2 || error.httpCode != 0 -> InteractionVideoResult.TransportError(error)
    else -> InteractionVideoResult.ApiError(error.code, error.message.orEmpty(), error.httpCode)
}

internal fun <T> parseInteractionResponse(
    response: JsonElement,
    parse: (InteractionJson) -> T
): InteractionVideoResult<T> = try {
    val root = InteractionJson(response.asJsonObject)
    val code = requireNotNull(root.optionalInt("code")) { "Missing response code" }
    if (code != 0) {
        interactionError(BilibiliApiException(code, root.string("message")))
    } else {
        val data = root.obj("data")
        val voucher = listOfNotNull(root.raw("v_voucher"), data?.raw("v_voucher")).firstOrNull {
            !it.isJsonPrimitive || it.asString.isNotBlank()
        }
        if (voucher != null) {
            InteractionVideoResult.VoucherRequired(voucher.deepCopy())
        } else {
            InteractionVideoResult.Success(parse(requireNotNull(data) { "Missing response data" }))
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (exception: Exception) {
    InteractionVideoResult.InvalidResponse(exception)
}

internal class InteractionJson(private val value: JsonObject) {
    fun has(name: String): Boolean = value.has(name)
    fun raw(name: String): JsonElement? = value.get(name)?.takeUnless { it.isJsonNull }
    fun obj(name: String): InteractionJson? = raw(name)?.let { InteractionJson(it.asJsonObject) }
    fun objects(name: String): List<InteractionJson> = raw(name)?.asJsonArray?.map {
        InteractionJson(it.asJsonObject)
    }.orEmpty()
    fun string(name: String): String = raw(name)?.asString.orEmpty()
    fun optionalLong(name: String): Long? = raw(name)?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "$name must be a number" }
        it.asBigDecimal.longValueExact()
    }
    fun long(name: String, default: Long = 0): Long = optionalLong(name) ?: default
    fun optionalInt(name: String): Int? = optionalLong(name)?.let {
        require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$name is out of range" }
        it.toInt()
    }
    fun int(name: String): Int = optionalInt(name) ?: 0
    fun optionalDouble(name: String): Double? = raw(name)?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "$name must be a number" }
        it.asDouble.also { number -> require(number.isFinite()) { "$name must be finite" } }
    }
    fun double(name: String): Double = optionalDouble(name) ?: 0.0
}
