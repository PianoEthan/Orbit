package com.qx.orbit.bili.data.model

import com.google.gson.JsonElement

data class InteractionVideo(
    val title: String = "",
    val edgeId: Long = 0,
    val storyList: List<InteractionStoryNode> = emptyList(),
    val edges: InteractionEdge? = null,
    val preload: InteractionPreload? = null,
    val hiddenVars: List<InteractionHiddenVar> = emptyList(),
    val isLeaf: Int = 0,
    val noTutorial: Int = 0,
    val noBacktracking: Int = 0,
    val noEvaluation: Int = 0
)

data class InteractionStoryNode(
    val nodeId: Long = 0,
    val edgeId: Long = 0,
    val title: String = "",
    val cid: Long = 0,
    val startPos: Long = 0,
    val cover: String = "",
    val isCurrent: Int = 0,
    val cursor: Long = 0
)

data class InteractionEdge(
    val dimension: InteractionDimension? = null,
    val questions: List<InteractionQuestion> = emptyList(),
    val skin: InteractionSkin? = null
)

data class InteractionDimension(
    val width: Int = 0,
    val height: Int = 0,
    val rotate: Int = 0,
    val sar: String = ""
)

data class InteractionQuestion(
    val id: Long = 0,
    val type: Int = 0,
    val startTime: Long? = null,
    val startTimeR: Long? = null,
    val duration: Long = -1,
    val pauseVideo: Int = 0,
    val title: String = "",
    val choices: List<InteractionChoice> = emptyList(),
    val fadeInTime: Long = 0,
    val fadeOutTime: Long = 0
)

data class InteractionChoice(
    val id: Long = 0,
    val platformAction: String = "",
    val nativeAction: String = "",
    val condition: String = "",
    val cid: Long = 0,
    val x: Double? = null,
    val y: Double? = null,
    val textAlign: Int = 0,
    val option: String = "",
    val selected: JsonElement? = null,
    val submited: JsonElement? = null,
    val isDefault: Int = 0,
    val isHidden: Int = 0
)

data class InteractionSkin(
    val choiceImage: String = "",
    val titleTextColor: String = "",
    val titleShadowColor: String = "",
    val titleShadowOffsetX: Double = 0.0,
    val titleShadowOffsetY: Double = 0.0,
    val titleShadowRadius: Double = 0.0,
    val progressbarColor: String = "",
    val progressbarShadowColor: String = ""
)

data class InteractionPreload(val video: List<InteractionPreloadVideo> = emptyList())

data class InteractionPreloadVideo(val aid: Long = 0, val cid: Long = 0)

data class InteractionHiddenVar(
    val value: Double = 0.0,
    val id: String = "",
    val idV2: String = "",
    val type: Int = 0,
    val isShow: Int = 0,
    val name: String = "",
    val skipOverwrite: Int? = null
)

sealed class InteractionVideoResult<out T> {
    data class Success<T>(val data: T) : InteractionVideoResult<T>()
    data class GraphInvalid(val message: String) : InteractionVideoResult<Nothing>()
    data class ApiError(val code: Int, val message: String, val httpCode: Int = 0) : InteractionVideoResult<Nothing>()
    data class VoucherRequired(val voucher: JsonElement) : InteractionVideoResult<Nothing>()
    data class TransportError(val cause: Exception) : InteractionVideoResult<Nothing>()
    data class InvalidResponse(val cause: Exception) : InteractionVideoResult<Nothing>()
}
