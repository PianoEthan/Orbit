package com.qx.orbit.bili.util.interaction

import com.qx.orbit.bili.data.model.InteractionVideo
import java.util.Collections
import java.util.UUID

class InteractionSession {
    data class Token(val session: String, val generation: Long, val sequence: Long)

    enum class ErrorKind { INVALID_DATA, UNSUPPORTED, EXPRESSION, LIMIT_EXCEEDED, TRANSITION }
    data class Problem(val kind: ErrorKind, val message: String)
    enum class Phase { TARGET, MEDIA, FAILED_TARGET, FAILED_MEDIA }

    data class Visit(
        val visitId: Long,
        val edgeId: Long,
        val cid: Long,
        val variables: Map<String, Double>
    )

    data class RootIdentity(val edgeId: Long, val cid: Long)

    data class Checkpoint(
        val graphVersion: Long,
        val edgeId: Long,
        val cid: Long,
        val variables: Map<String, Double>,
        val root: RootIdentity,
        val visits: List<Visit> = emptyList()
    )

    data class Question(
        val index: Int,
        val visibleChoiceIndices: List<Int>,
        val eligibleChoiceIndices: List<Int>,
        val remainingMs: Long?,
        val pauseRequested: Boolean
    )

    data class Transition(
        val token: Token,
        val edgeId: Long,
        val cid: Long,
        val questionIndex: Int?,
        val choiceIndex: Int?,
        val candidateVariables: Map<String, Double>,
        val phase: Phase
    )

    data class State(
        val graphVersion: Long? = null,
        val edgeId: Long? = null,
        val cid: Long? = null,
        val visitId: Long? = null,
        val variables: Map<String, Double> = emptyMap(),
        val completedQuestionIndices: Set<Int> = emptySet(),
        val question: Question? = null,
        val transition: Transition? = null,
        val visits: List<Visit> = emptyList(),
        val problem: Problem? = null,
        val generation: Long = 0
    )

    sealed interface Event
    data object Ignored : Event
    data class Error(val problem: Problem) : Event
    data class Target(val edgeId: Long, val cid: Long, val token: Token) : Event
    data class PrepareMedia(val edgeId: Long, val cid: Long, val token: Token, val seekToMs: Long = 0) : Event
    data class QuestionShown(val question: Question) : Event
    data class Committed(val visit: Visit) : Event
    data object QuestionDismissed : Event

    @Volatile
    var state: State = State()
        private set

    private val sessionId = UUID.randomUUID().toString()
    private var generation = 0L
    private var sequence = 0L
    private var visitSequence = 0L
    private var automaticTransitions = 0
    private var node: InteractionVideo? = null
    private var stagedNode: InteractionVideo? = null
    private var root: InteractionVideo? = null
    private var rootCid = 0L
    private var lastNowMs: Long? = null
    private var countdownWasAdvancing = false

    @Synchronized
    fun load(
        graphVersion: Long,
        initialNode: InteractionVideo,
        cid: Long,
        checkpoint: Checkpoint? = null,
        rootNode: InteractionVideo? = null
    ): Event {
        reset()
        if (graphVersion <= 0 || cid <= 0) return fail(ErrorKind.INVALID_DATA, "Invalid graph version or CID")
        validateNode(initialNode)?.let { return fail(it) }
        val identity = checkpoint?.root ?: RootIdentity(initialNode.edgeId, cid)
        if (identity.edgeId <= 0 || identity.cid <= 0) return fail(ErrorKind.INVALID_DATA, "Invalid root identity")
        val initialRoot = rootNode ?: initialNode.takeIf { identity == RootIdentity(it.edgeId, cid) }
            ?: return fail(ErrorKind.INVALID_DATA, "Restoring a non-root node requires rootNode")
        if (initialRoot.edgeId != identity.edgeId) return fail(ErrorKind.INVALID_DATA, "Root node does not match checkpoint root")
        validateNode(initialRoot)?.let { return fail(it) }
        val rootVariables = mergeVariables(initialRoot, emptyMap())
        if (rootVariables is Variables.Invalid) return fail(rootVariables.problem)
        if (checkpoint != null) {
            if (checkpoint.graphVersion != graphVersion || checkpoint.edgeId != initialNode.edgeId || checkpoint.cid != cid) {
                return fail(ErrorKind.INVALID_DATA, "Checkpoint does not match graph, edge and CID")
            }
            validateVariables(checkpoint.variables)?.let { return fail(it) }
            if (checkpoint.visits.size > MAX_VISITS || checkpoint.visits.any {
                    it.visitId <= 0 || it.visitId > MAX_VISIT_ID || it.edgeId <= 0 || it.cid <= 0 || validateVariables(it.variables) != null
                } || checkpoint.visits.zipWithNext().any { (previous, next) -> previous.visitId >= next.visitId }) {
                return fail(ErrorKind.INVALID_DATA, "Invalid checkpoint history")
            }
        }
        val variables = mergeVariables(initialNode, checkpoint?.variables.orEmpty())
        if (variables is Variables.Invalid) return fail(variables.problem)
        variables as Variables.Valid
        root = freezeNode(initialRoot)
        rootCid = identity.cid
        stagedNode = freezeNode(initialNode)
        val rebase = checkpoint?.visits?.lastOrNull()?.visitId == MAX_VISIT_ID
        val visits = immutableList(checkpoint?.visits.orEmpty().mapIndexed { index, visit ->
            visit.copy(visitId = if (rebase) index + 1L else visit.visitId, variables = immutableMap(visit.variables))
        })
        visitSequence = visits.lastOrNull()?.visitId ?: 0
        state = State(graphVersion = graphVersion, visits = visits, generation = generation)
        val token = nextToken()
        state = state.copy(transition = Transition(token, initialNode.edgeId, cid, null, null, variables.values, Phase.MEDIA))
        return PrepareMedia(initialNode.edgeId, cid, token)
    }

    @Synchronized
    fun reset() {
        check(generation < Long.MAX_VALUE) { "Session generation exhausted" }
        generation++
        node = null
        stagedNode = null
        root = null
        rootCid = 0
        state = State(generation = generation)
        visitSequence = 0
        clearClock()
        automaticTransitions = 0
    }

    @Synchronized
    fun restart(): Event {
        val initial = root ?: return Ignored
        val graphVersion = state.graphVersion ?: return Ignored
        return load(graphVersion, initial, rootCid)
    }

    @Synchronized
    fun checkpoint(): Checkpoint? {
        val edgeId = state.edgeId ?: return null
        return Checkpoint(state.graphVersion!!, edgeId, state.cid!!, state.variables, RootIdentity(root!!.edgeId, rootCid), state.visits)
    }

    @Synchronized
    fun observePlayback(
        positionMs: Long,
        durationMs: Long,
        ended: Boolean,
        nowMs: Long,
        canAdvanceCountdown: Boolean,
        discontinuity: Boolean = false
    ): Event {
        if (state.transition != null || state.problem != null) return Ignored
        val current = node ?: return Ignored
        if (positionMs < 0 || durationMs < 0 || nowMs < 0) return fail(ErrorKind.INVALID_DATA, "Negative playback observation")
        val questions = current.edges?.questions.orEmpty()
        state.question?.let { active ->
            val window = InteractionTimeline.window(questions[active.index], durationMs)
            if (window !is InteractionTimeline.Window) return fail(ErrorKind.UNSUPPORTED, "Unsupported question window")
            val outside = !window.contains(positionMs, ended)
            if (outside && (discontinuity || positionMs < window.startMs || window.requiresEnded && !ended)) {
                state = state.copy(question = null)
                clearClock()
                return QuestionDismissed
            }
            val previousNow = lastNowMs
            val monotonicNow = maxOf(previousNow ?: nowMs, nowMs)
            val elapsed = if (!discontinuity && canAdvanceCountdown && countdownWasAdvancing && previousNow != null) {
                monotonicNow - previousNow
            } else 0
            lastNowMs = monotonicNow
            countdownWasAdvancing = canAdvanceCountdown
            active.remainingMs?.let { remaining ->
                val next = (remaining - minOf(remaining, elapsed)).coerceAtLeast(0)
                state = state.copy(question = active.copy(remainingMs = next))
                if (next == 0L) {
                    val default = active.eligibleChoiceIndices.firstOrNull { questions[active.index].choices[it].isDefault == 1 }
                        ?: return fail(ErrorKind.INVALID_DATA, "Timed question has no eligible default")
                    return selectInternal(active.index, default, automatic = true)
                }
            }
            if (outside) {
                state = state.copy(question = null)
                clearClock()
                return QuestionDismissed
            }
            return Ignored
        }
        for ((index, question) in questions.withIndex()) {
            if (index in state.completedQuestionIndices) continue
            val window = InteractionTimeline.window(question, durationMs)
            if (window is InteractionTimeline.Unsupported) return fail(ErrorKind.UNSUPPORTED, window.message)
            window as InteractionTimeline.Window
            if (!window.contains(positionMs, ended)) continue
            state = state.copy(completedQuestionIndices = immutableSet(state.completedQuestionIndices + index))
            if (window.automatic) {
                val default = question.choices.indexOfFirst { it.isDefault == 1 }
                if (default == -1) return fail(ErrorKind.INVALID_DATA, "Automatic question has no default")
                state = state.copy(question = Question(index, emptyList(), immutableList(listOf(default)), null, false))
                return selectInternal(index, default, automatic = true)
            }
            var candidate = state.variables
            val eligible = mutableListOf<Int>()
            for ((choiceIndex, choice) in question.choices.withIndex()) {
                if (choice.condition.isBlank()) {
                    eligible += choiceIndex
                } else {
                    when (val result = InteractionExpression.evaluate(choice.condition, candidate)) {
                        is InteractionExpression.Failure -> return fail(ErrorKind.EXPRESSION, result.message)
                        is InteractionExpression.Success -> {
                            validateVariables(result.variables)?.let { return fail(it) }
                            candidate = result.variables
                            if (result.value != 0.0) eligible += choiceIndex
                        }
                    }
                }
            }
            val visible = eligible.filter { question.choices[it].isHidden == 0 }
            if (visible.isEmpty() && (window.countdownMs == null || eligible.none { question.choices[it].isDefault == 1 })) {
                return fail(ErrorKind.INVALID_DATA, "Question has no selectable choice or timed default")
            }
            val active = Question(index, immutableList(visible), immutableList(eligible), window.countdownMs, window.pauseRequested)
            state = state.copy(variables = immutableMap(candidate), question = active)
            lastNowMs = nowMs
            countdownWasAdvancing = canAdvanceCountdown
            return QuestionShown(active)
        }
        return Ignored
    }

    @Synchronized
    fun select(
        questionIndex: Int,
        choiceIndex: Int,
        visitId: Long? = state.visitId,
        generation: Long = state.generation
    ): Event = if (visitId != state.visitId || generation != state.generation) Ignored
    else selectInternal(questionIndex, choiceIndex, automatic = false)

    private fun selectInternal(questionIndex: Int, choiceIndex: Int, automatic: Boolean): Event {
        if (state.transition != null || state.problem != null) return Ignored
        val active = state.question ?: return Ignored
        if (active.index != questionIndex || choiceIndex !in if (automatic) active.eligibleChoiceIndices else active.visibleChoiceIndices) return Ignored
        if (automatic && automaticTransitions >= MAX_AUTOMATIC_TRANSITIONS) {
            return fail(ErrorKind.LIMIT_EXCEEDED, "Automatic transition limit reached; explicit restart required")
        }
        val choice = node!!.edges!!.questions[questionIndex].choices[choiceIndex]
        if (choice.platformAction.isNotBlank()) {
            val action = Regex("JUMP\\s+([0-9]+)\\s+([0-9]+)").matchEntire(choice.platformAction.trim())
                ?: return fail(ErrorKind.UNSUPPORTED, "Unsupported platform_action")
            if (action.groupValues[1].toLongOrNull() != choice.id || action.groupValues[2].toLongOrNull() != choice.cid) {
                return fail(ErrorKind.INVALID_DATA, "platform_action does not match target edge and CID")
            }
        }
        val candidate = when (val result = InteractionExpression.evaluate(choice.nativeAction, state.variables)) {
            is InteractionExpression.Failure -> return fail(ErrorKind.EXPRESSION, result.message)
            is InteractionExpression.Success -> result.variables
        }
        validateVariables(candidate)?.let { return fail(it) }
        if (automatic) automaticTransitions++ else automaticTransitions = 0
        val token = nextToken()
        state = state.copy(transition = Transition(token, choice.id, choice.cid, questionIndex, choiceIndex, immutableMap(candidate), Phase.TARGET))
        clearClock()
        return Target(choice.id, choice.cid, token)
    }

    @Synchronized
    fun targetReceived(token: Token, targetNode: InteractionVideo): Event {
        val transition = state.transition ?: return Ignored
        if (transition.token != token || transition.phase != Phase.TARGET) return Ignored
        if (targetNode.edgeId != transition.edgeId) return transitionProblem("Response edge does not match target")
        validateNode(targetNode)?.let { return transitionProblem(it) }
        val merged = mergeVariables(targetNode, transition.candidateVariables)
        if (merged is Variables.Invalid) return transitionProblem(merged.problem)
        merged as Variables.Valid
        stagedNode = freezeNode(targetNode)
        val mediaToken = nextToken()
        state = state.copy(transition = transition.copy(token = mediaToken, phase = Phase.MEDIA, candidateVariables = merged.values))
        return PrepareMedia(transition.edgeId, transition.cid, mediaToken)
    }

    @Synchronized
    fun mediaPrepared(token: Token): Event {
        val transition = state.transition ?: return Ignored
        if (transition.token != token || transition.phase != Phase.MEDIA) return Ignored
        val targetNode = stagedNode ?: return Ignored
        if (visitSequence >= MAX_VISIT_ID) return transitionProblem(Problem(ErrorKind.LIMIT_EXCEEDED, "Visit sequence exhausted; restart or reload checkpoint"))
        val visit = Visit(++visitSequence, transition.edgeId, transition.cid, transition.candidateVariables)
        val visits = immutableList((state.visits.takeLast(MAX_VISITS - 1) + visit))
        node = targetNode
        stagedNode = null
        state = State(state.graphVersion, visit.edgeId, visit.cid, visit.visitId, visit.variables, visits = visits, generation = generation)
        clearClock()
        return Committed(visit)
    }

    @Synchronized
    fun transitionFailed(token: Token, message: String): Event {
        val transition = state.transition ?: return Ignored
        if (transition.token != token || transition.phase !in listOf(Phase.TARGET, Phase.MEDIA)) return Ignored
        return transitionProblem(message)
    }

    @Synchronized
    fun retry(): Event {
        val transition = state.transition ?: return Ignored
        val phase = when (transition.phase) {
            Phase.FAILED_TARGET -> Phase.TARGET
            Phase.FAILED_MEDIA -> Phase.MEDIA
            else -> return Ignored
        }
        val token = nextToken()
        state = state.copy(transition = transition.copy(token = token, phase = phase), problem = null)
        return if (phase == Phase.TARGET) Target(transition.edgeId, transition.cid, token)
        else PrepareMedia(transition.edgeId, transition.cid, token)
    }

    private fun transitionProblem(message: String): Event = transitionProblem(Problem(ErrorKind.TRANSITION, message))

    private fun transitionProblem(problem: Problem): Event {
        val transition = state.transition!!
        val phase = if (transition.phase == Phase.TARGET) Phase.FAILED_TARGET else Phase.FAILED_MEDIA
        state = state.copy(transition = transition.copy(phase = phase), problem = problem)
        return Error(problem)
    }

    private fun fail(kind: ErrorKind, message: String): Event = fail(Problem(kind, message))

    private fun fail(problem: Problem): Event {
        state = state.copy(problem = problem)
        clearClock()
        return Error(problem)
    }

    private fun nextToken(): Token {
        check(sequence < Long.MAX_VALUE) { "Token sequence exhausted" }
        return Token(sessionId, generation, ++sequence)
    }

    private fun clearClock() {
        lastNowMs = null
        countdownWasAdvancing = false
    }

    private sealed interface Variables {
        data class Valid(val values: Map<String, Double>) : Variables
        data class Invalid(val problem: Problem) : Variables
    }

    private fun mergeVariables(node: InteractionVideo, existing: Map<String, Double>): Variables {
        val merged = existing.toMutableMap()
        for (variable in node.hiddenVars) {
            if (variable.type !in 1..2) return Variables.Invalid(Problem(ErrorKind.UNSUPPORTED, "Variable type ${variable.type}"))
            if (variable.skipOverwrite != null && variable.skipOverwrite != 0) {
                return Variables.Invalid(Problem(ErrorKind.UNSUPPORTED, "skip_overwrite=${variable.skipOverwrite}"))
            }
            val key = variable.idV2.ifBlank { variable.id }
            if (key.isBlank() || key.length > MAX_VARIABLE_NAME || !variable.value.isFinite()) {
                return Variables.Invalid(Problem(ErrorKind.INVALID_DATA, "Invalid hidden variable"))
            }
            merged.putIfAbsent(key, variable.value)
        }
        validateVariables(merged)?.let { return Variables.Invalid(it) }
        return Variables.Valid(immutableMap(merged))
    }

    private fun validateVariables(variables: Map<String, Double>): Problem? = when {
        variables.size > MAX_VARIABLES -> Problem(ErrorKind.LIMIT_EXCEEDED, "Too many variables")
        variables.any { it.key.isBlank() || it.key.length > MAX_VARIABLE_NAME || !it.value.isFinite() } ->
            Problem(ErrorKind.INVALID_DATA, "Invalid variable map")
        else -> null
    }

    private fun validateNode(node: InteractionVideo): Problem? {
        if (node.edgeId <= 0) return Problem(ErrorKind.INVALID_DATA, "Invalid edge ID")
        if (node.storyList.size > MAX_STORY_NODES || node.hiddenVars.size > MAX_VARIABLES ||
            node.preload?.video.orEmpty().size > MAX_STORY_NODES || node.edges?.questions.orEmpty().size > MAX_QUESTIONS) {
            return Problem(ErrorKind.LIMIT_EXCEEDED, "Node size exceeds limits")
        }
        if (node.storyList.any { it.edgeId <= 0 || it.cid <= 0 } || node.preload?.video.orEmpty().any { it.cid <= 0 }) {
            return Problem(ErrorKind.INVALID_DATA, "Invalid story edge or CID")
        }
        var totalChoices = 0
        var totalSourceLength = 0
        for (question in node.edges?.questions.orEmpty()) {
            if (question.choices.size > MAX_CHOICES) return Problem(ErrorKind.LIMIT_EXCEEDED, "Too many choices")
            totalChoices += question.choices.size
            if (totalChoices > MAX_TOTAL_CHOICES) return Problem(ErrorKind.LIMIT_EXCEEDED, "Too many node choices")
            if (question.type !in listOf(0, 1, 2, 4)) return Problem(ErrorKind.UNSUPPORTED, "Question type ${question.type}")
            for (choice in question.choices) {
                if (choice.id <= 0 || choice.cid <= 0) return Problem(ErrorKind.INVALID_DATA, "Invalid target edge or CID")
                if (choice.condition.length > InteractionExpression.MAX_SOURCE_LENGTH ||
                    choice.nativeAction.length > InteractionExpression.MAX_SOURCE_LENGTH ||
                    choice.platformAction.length > InteractionExpression.MAX_SOURCE_LENGTH) {
                    return Problem(ErrorKind.LIMIT_EXCEEDED, "Choice expression exceeds limits")
                }
                totalSourceLength += choice.condition.length + choice.nativeAction.length + choice.platformAction.length
                if (totalSourceLength > MAX_TOTAL_SOURCE_LENGTH) return Problem(ErrorKind.LIMIT_EXCEEDED, "Node expressions exceed limits")
                if (choice.isHidden !in 0..1 || choice.isDefault !in 0..1) return Problem(ErrorKind.UNSUPPORTED, "Choice flags")
            }
        }
        return null
    }

    private fun freezeNode(node: InteractionVideo): InteractionVideo = node.copy(
        storyList = immutableList(node.storyList),
        hiddenVars = immutableList(node.hiddenVars),
        preload = node.preload?.copy(video = immutableList(node.preload.video)),
        edges = node.edges?.copy(questions = immutableList(node.edges.questions.map { question ->
            question.copy(choices = immutableList(question.choices.map { it.copy(selected = null, submited = null) }))
        }))
    )

    companion object {
        const val MAX_VARIABLES = 512
        const val MAX_VARIABLE_NAME = 128
        const val MAX_STORY_NODES = 1_024
        const val MAX_QUESTIONS = 128
        const val MAX_CHOICES = 128
        const val MAX_TOTAL_CHOICES = 1_024
        const val MAX_TOTAL_SOURCE_LENGTH = 262_144
        const val MAX_VISITS = 128
        const val MAX_VISIT_ID = Long.MAX_VALUE - 2
        const val MAX_AUTOMATIC_TRANSITIONS = 32

        private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
        private fun <T> immutableList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
        private fun <T> immutableSet(values: Set<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
    }
}
