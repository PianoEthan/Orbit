package com.qx.orbit.bili.data.model

data class InteractionVideoData(
    val title: String = "",
    val edgeId: Long = 0,
    val storyList: List<StoryNode> = emptyList(),
    val edges: InteractionEdge? = null,
    val isLeaf: Int = 0
) {
    data class StoryNode(
        val nodeId: Long = 0, val edgeId: Long = 0, val title: String = "",
        val cid: Long = 0, val startPos: Long = 0, val cover: String = "",
        val isCurrent: Int = 0
    )

    data class InteractionEdge(
        val questions: List<Question> = emptyList()
    )

    data class Question(
        val id: Long = 0, val type: Int = 0, val startTimeR: Long = 0,
        val duration: Long = 0, val pauseVideo: Int = 0, val title: String = "",
        val choices: List<Choice> = emptyList()
    )

    data class Choice(
        val id: Long = 0, val cid: Long = 0, val option: String = "",
        val isDefault: Int = 0, val isHidden: Int = 0
    )
}
