package com.qx.orbit.bili.util.interaction

import com.qx.orbit.bili.data.model.InteractionChoice
import com.qx.orbit.bili.data.model.InteractionEdge
import com.qx.orbit.bili.data.model.InteractionHiddenVar
import com.qx.orbit.bili.data.model.InteractionQuestion
import com.qx.orbit.bili.data.model.InteractionStoryNode
import com.qx.orbit.bili.data.model.InteractionVideo
import com.qx.orbit.bili.util.interaction.InteractionSession.Committed
import com.qx.orbit.bili.util.interaction.InteractionSession.Error
import com.qx.orbit.bili.util.interaction.InteractionSession.ErrorKind
import com.qx.orbit.bili.util.interaction.InteractionSession.Ignored
import com.qx.orbit.bili.util.interaction.InteractionSession.PrepareMedia
import com.qx.orbit.bili.util.interaction.InteractionSession.QuestionDismissed
import com.qx.orbit.bili.util.interaction.InteractionSession.QuestionShown
import com.qx.orbit.bili.util.interaction.InteractionSession.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionSessionTest {
    @Test
    fun unconditionalChoicesAreIndexedEvenWithRepeatedTargetIds() {
        val session = ready(node(question(choices = listOf(choice(action = "x=1"), choice(action = "x=2")))))
        val shown = tick(session) as QuestionShown
        assertEquals(listOf(0, 1), shown.question.visibleChoiceIndices)
        assertEquals(Ignored, session.select(7, 0))
        assertEquals(Ignored, session.select(0, 9))
        val target = session.select(0, 1) as Target
        assertEquals(2L, target.edgeId)
        assertEquals(2.0, session.state.transition!!.candidateVariables["x"])
        assertTrue(session.state.variables.isEmpty())
        assertEquals(1, session.state.transition!!.choiceIndex)
        assertEquals(Ignored, session.select(0, 0))
        assertEquals(Ignored, tick(session, now = 99_999))
    }

    @Test
    fun initialLoadAndEveryTransitionCommitOnlyAfterMediaReady() {
        val session = InteractionSession()
        val initial = session.load(7, node(question()), 10) as PrepareMedia
        assertNull(session.state.edgeId)
        assertNull(session.state.visitId)
        assertTrue(session.mediaPrepared(initial.token) is Committed)
        val source = session.state
        tick(session)
        val target = session.select(0, 0) as Target
        assertEquals(Ignored, session.mediaPrepared(target.token))
        val media = session.targetReceived(target.token, node(edge = 2)) as PrepareMedia
        assertNotEquals(target.token, media.token)
        assertEquals(source.edgeId, session.state.edgeId)
        assertEquals(source.cid, session.state.cid)
        assertEquals(Ignored, session.targetReceived(target.token, node(edge = 2)))
        assertEquals(Ignored, session.transitionFailed(target.token, "late target failure"))
        assertEquals(Ignored, session.mediaPrepared(initial.token))
        session.mediaPrepared(media.token)
        assertEquals(2L, session.state.edgeId)
        assertEquals(20L, session.state.cid)
        assertTrue(session.state.visitId!! > source.visitId!!)
        assertEquals(Ignored, session.mediaPrepared(media.token))
    }

    @Test
    fun edgeAndMediaFailuresRetryWithoutRepeatingNativeActionOrLosingOptions() {
        val session = ready(node(question(choices = listOf(choice(action = "x=x-3")))), variables = listOf(variable("x", 10.0)))
        tick(session)
        val target = session.select(0, 0) as Target
        session.transitionFailed(target.token, "edge offline")
        assertEquals(10.0, session.state.variables["x"])
        assertEquals(listOf(0), session.state.question!!.visibleChoiceIndices)
        assertEquals(7.0, session.state.transition!!.candidateVariables["x"])
        assertEquals(Ignored, session.select(0, 0))
        val retryTarget = session.retry() as Target
        assertNotEquals(target.token, retryTarget.token)
        assertEquals(Ignored, session.targetReceived(target.token, node(edge = 2)))
        assertEquals(Ignored, session.transitionFailed(target.token, "old error"))
        val media = session.targetReceived(retryTarget.token, node(edge = 2)) as PrepareMedia
        session.transitionFailed(media.token, "decoder failed")
        assertEquals(1L, session.state.edgeId)
        assertEquals(10L, session.state.cid)
        assertEquals(10.0, session.state.variables["x"])
        assertEquals(7.0, session.state.transition!!.candidateVariables["x"])
        val retryMedia = session.retry() as PrepareMedia
        assertNotEquals(media.token, retryMedia.token)
        assertEquals(Ignored, session.mediaPrepared(media.token))
        assertEquals(Ignored, session.transitionFailed(media.token, "old media error"))
        session.mediaPrepared(retryMedia.token)
        assertEquals(7.0, session.state.variables["x"])
        assertNull(session.state.question)
        assertNull(session.state.problem)
        assertEquals(Ignored, session.retry())
    }

    @Test
    fun wrongEdgeResponseFailsAndCanRetryButNeverStagesWrongMedia() {
        val session = ready(node(question()))
        tick(session)
        val target = session.select(0, 0) as Target
        assertTrue(session.targetReceived(target.token, node(edge = 3)) is Error)
        assertEquals(1L, session.state.edgeId)
        assertEquals(Ignored, session.mediaPrepared(target.token))
        val retry = session.retry() as Target
        val media = session.targetReceived(retry.token, node(edge = 2)) as PrepareMedia
        session.mediaPrepared(media.token)
        assertEquals(2L, session.state.edgeId)
    }

    @Test
    fun sameCidAndSelfLoopAlwaysReprepareAtZeroAndCreateNewVisit() {
        val self = node(question(choices = listOf(choice(edge = 1, cid = 10, action = "x=x+1"))))
        val session = ready(self)
        repeat(3) {
            val visitId = session.state.visitId!!
            tick(session)
            val target = session.select(0, 0) as Target
            val media = session.targetReceived(target.token, self) as PrepareMedia
            assertEquals(10L, media.cid)
            assertEquals(0L, media.seekToMs)
            assertEquals(visitId, session.state.visitId)
            session.mediaPrepared(media.token)
            assertTrue(session.state.visitId!! > visitId)
            assertEquals((it + 1).toDouble(), session.state.variables["x"])
            assertTrue(session.state.completedQuestionIndices.isEmpty())
        }
        val other = node(question(choices = listOf(choice(cid = 10))))
        val shared = ready(other)
        tick(shared)
        val target = shared.select(0, 0) as Target
        val media = shared.targetReceived(target.token, node(edge = 2)) as PrepareMedia
        assertEquals(10L, media.cid)
        shared.mediaPrepared(media.token)
        assertEquals(2L, shared.state.edgeId)
        assertEquals(10L, shared.state.cid)
    }

    @Test
    fun resetRestartGraphReloadAndOtherSessionTokensCannotMutateCurrentSession() {
        val session = ready(node(question()))
        tick(session)
        val oldTarget = session.select(0, 0) as Target
        val restart = session.restart() as PrepareMedia
        assertTrue(restart.token.sequence > oldTarget.token.sequence)
        assertTrue(restart.token.generation > oldTarget.token.generation)
        assertEquals(Ignored, session.targetReceived(oldTarget.token, node(edge = 2)))
        assertEquals(Ignored, session.transitionFailed(oldTarget.token, "stale"))
        session.mediaPrepared(restart.token)
        val next = session.load(8, node(edge = 8), 80) as PrepareMedia
        assertEquals(Ignored, session.mediaPrepared(restart.token))
        assertEquals(Ignored, session.transitionFailed(restart.token, "stale"))
        session.mediaPrepared(next.token)
        assertEquals(8L, session.state.graphVersion)
        assertEquals(8L, session.state.edgeId)
        session.reset()
        val reloaded = session.load(7, node(), 10) as PrepareMedia
        assertTrue(reloaded.token.sequence > next.token.sequence)
        assertEquals(Ignored, session.mediaPrepared(next.token))
        val other = InteractionSession().load(7, node(), 10) as PrepareMedia
        assertNotEquals(other.token, reloaded.token)
        assertEquals(Ignored, session.mediaPrepared(other.token))
        session.mediaPrepared(reloaded.token)
        assertEquals(1L, session.state.edgeId)
    }

    @Test
    fun initialMediaFailureCanRetryWithoutAnyCommittedVisit() {
        val session = InteractionSession()
        val media = session.load(7, node(), 10) as PrepareMedia
        session.transitionFailed(media.token, "initial media failed")
        assertNull(session.state.visitId)
        val retry = session.retry() as PrepareMedia
        assertEquals(Ignored, session.mediaPrepared(media.token))
        session.mediaPrepared(retry.token)
        assertEquals(1, session.state.visits.size)
    }

    @Test
    fun automaticDefaultIsNotFirstAndBypassesBothConditionAndHiddenFiltering() {
        val session = ready(node(question(type = 0, choices = listOf(
            choice(edge = 2, condition = "x=999"),
            choice(edge = 3, condition = "1/0", action = "x=x+1", default = 1, hidden = 1)
        ))))
        assertEquals(Ignored, tick(session, ended = false))
        val target = tick(session) as Target
        assertEquals(3L, target.edgeId)
        assertEquals(1, session.state.transition!!.choiceIndex)
        assertEquals(1.0, session.state.transition!!.candidateVariables["x"])
        assertFalse(session.state.variables.containsKey("x"))
        assertEquals(Ignored, tick(session, now = 100_000))
    }

    @Test
    fun automaticMissingDefaultIsAnErrorInsteadOfSelectingFirst() {
        val session = ready(node(question(type = 0)))
        assertEquals(ErrorKind.INVALID_DATA, (tick(session) as Error).problem.kind)
        assertNull(session.state.transition)
        assertEquals(Ignored, tick(session))
    }

    @Test
    fun conditionSideEffectsRunInOrderOnlyOnceOnTrueEntry() {
        val session = ready(node(question(choices = listOf(
            choice(condition = "x=x+1;0"),
            choice(condition = "x==1"),
            choice(condition = "x=x+2;1", hidden = 1),
            choice(condition = "x==3")
        ))))
        assertEquals(Ignored, tick(session, ended = false))
        assertTrue(session.state.variables.isEmpty())
        val shown = tick(session) as QuestionShown
        assertEquals(listOf(1, 3), shown.question.visibleChoiceIndices)
        assertEquals(listOf(1, 2, 3), shown.question.eligibleChoiceIndices)
        assertEquals(3.0, session.state.variables["x"])
        repeat(5) { assertEquals(Ignored, tick(session, now = it * 1_000L)) }
        assertEquals(3.0, session.state.variables["x"])
        assertEquals(Ignored, session.select(0, 2))
    }

    @Test
    fun failedConditionRoundDoesNotPartiallyPolluteCommittedVariables() {
        val session = ready(node(question(choices = listOf(choice(condition = "x=9"), choice(condition = "y=2;1/0")))), variables = listOf(variable("x", 5.0)))
        assertEquals(ErrorKind.EXPRESSION, (tick(session) as Error).problem.kind)
        assertEquals(mapOf("x" to 5.0), session.state.variables)
        assertNull(session.state.question)
        assertEquals(Ignored, tick(session))
        assertEquals(Ignored, session.select(0, 0))
    }

    @Test
    fun failedNativeActionDoesNotPartiallyPolluteCommittedVariables() {
        val session = ready(node(question(choices = listOf(choice(action = "x=9;1/0")))), variables = listOf(variable("x", 5.0)))
        tick(session)
        assertEquals(ErrorKind.EXPRESSION, (session.select(0, 0) as Error).problem.kind)
        assertEquals(5.0, session.state.variables["x"])
        assertNull(session.state.transition)
    }

    @Test
    fun timedDefaultUsesFilteredChoicesAndCanKeepAnEligibleHiddenDefault() {
        val session = ready(node(question(duration = 2_000, choices = listOf(
            choice(edge = 2, condition = "0", default = 1),
            choice(edge = 3, condition = "1", default = 1, hidden = 1),
            choice(edge = 4)
        ))))
        assertEquals(Ignored, tick(session, position = 7_999, ended = false))
        val shown = tick(session, position = 8_000, ended = false, now = 1_000) as QuestionShown
        assertEquals(listOf(2), shown.question.visibleChoiceIndices)
        assertEquals(2_000L, shown.question.remainingMs)
        assertEquals(Ignored, session.select(0, 1))
        val target = tick(session, position = 9_000, ended = false, now = 3_000) as Target
        assertEquals(3L, target.edgeId)
        assertEquals(Ignored, tick(session, now = 99_000))
    }

    @Test
    fun timedQuestionWithOnlyInvalidDefaultErrorsAtExpiry() {
        val session = ready(node(question(duration = 1_000, choices = listOf(choice(condition = "0", default = 1), choice()))))
        tick(session, position = 9_000, ended = false, now = 0)
        assertEquals(ErrorKind.INVALID_DATA, (tick(session, position = 9_500, ended = false, now = 1_000) as Error).problem.kind)
        assertNull(session.state.transition)
    }

    @Test
    fun countdownUsesWallTimeFreezesForHostPauseBackgroundBufferAndIgnoresClockRegression() {
        val session = ready(node(question(duration = 3_000, choices = listOf(choice(default = 1)))))
        tick(session, position = 7_000, ended = false, now = 10_000)
        tick(session, position = 7_100, ended = false, now = 11_000)
        assertEquals(2_000L, session.state.question!!.remainingMs)
        tick(session, position = 7_100, ended = false, now = 11_000, advancing = false)
        tick(session, position = 7_100, ended = false, now = 99_000, advancing = false)
        tick(session, position = 7_100, ended = false, now = 100_000, advancing = true)
        assertEquals(2_000L, session.state.question!!.remainingMs)
        tick(session, position = 7_100, ended = false, now = 99_000)
        assertEquals(2_000L, session.state.question!!.remainingMs)
        tick(session, position = 7_100, ended = false, now = 101_999)
        assertEquals(1L, session.state.question!!.remainingMs)
        assertTrue(tick(session, position = 7_100, ended = false, now = 102_000) is Target)
    }

    @Test
    fun delayedEntryStillGetsFullCountdownAndQuestionPauseDoesNotImplicitlyFreezeTimer() {
        val session = ready(node(question(duration = 2_000, choices = listOf(choice(default = 1)), pause = 1)))
        val shown = tick(session, position = 9_900, ended = false, now = 8_000) as QuestionShown
        assertFalse(shown.question.pauseRequested)
        assertEquals(2_000L, shown.question.remainingMs)
        tick(session, position = 10_000, ended = true, now = 9_999)
        assertEquals(1L, session.state.question!!.remainingMs)
        assertTrue(tick(session, position = 10_000, ended = true, now = 10_000) is Target)
    }

    @Test
    fun seekOutCancelsExpiredTimerBeforeAutoSelectionAndDoesNotRepeatEntrySideEffects() {
        val session = ready(node(question(duration = 2_000, choices = listOf(choice(condition = "x=x+1;1", default = 1)))))
        tick(session, position = 8_000, ended = false, now = 0)
        assertEquals(QuestionDismissed, tick(session, position = 1_000, ended = false, now = 100_000))
        assertNull(session.state.question)
        assertNull(session.state.transition)
        assertEquals(Ignored, tick(session, position = 8_500, ended = false, now = 101_000))
        assertEquals(1.0, session.state.variables["x"])
    }

    @Test
    fun typeFourPauseAndUnpausedWindowsAndMultipleQuestionOrdering() {
        val first = question(type = 4, start = 1_000, duration = 1_000, pause = 1)
        val second = question(type = 4, start = 1_500, duration = 2_000, choices = listOf(choice(default = 1)))
        val session = ready(node(first, second))
        assertEquals(Ignored, tick(session, position = 999, ended = false))
        val shown = tick(session, position = 1_000, ended = false) as QuestionShown
        assertEquals(0, shown.question.index)
        assertTrue(shown.question.pauseRequested)
        assertNull(shown.question.remainingMs)
        assertEquals(Ignored, tick(session, position = 1_900, ended = false, now = 50_000))
        assertEquals(0, session.state.question!!.index)
        assertEquals(QuestionDismissed, tick(session, position = 2_000, ended = false, now = 60_000))
        val next = tick(session, position = 2_000, ended = false, now = 60_001) as QuestionShown
        assertEquals(1, next.question.index)
        assertEquals(2_000L, next.question.remainingMs)
        assertFalse(next.question.pauseRequested)
        assertTrue(tick(session, position = 2_000, ended = false, now = 62_001) is Target)
    }

    @Test
    fun typeFourSeekPastWindowCancelsWithoutAutoChoosingAndReplayGetsNewQuestion() {
        val source = node(question(type = 4, start = 1_000, duration = 1_000, choices = listOf(choice(default = 1))))
        val session = ready(source)
        tick(session, position = 1_000, ended = false)
        assertEquals(QuestionDismissed, tick(session, position = 2_000, ended = false, now = 10_000, discontinuity = true))
        assertEquals(Ignored, tick(session, position = 1_000, ended = false, now = 11_000))
        val media = session.restart() as PrepareMedia
        session.mediaPrepared(media.token)
        assertTrue(tick(session, position = 1_000, ended = false) is QuestionShown)
    }

    @Test
    fun zeroDurationAndUntimedQuestionsNeverInventCountdown() {
        for (type in 1..2) {
            val session = ready(node(question(type = type, duration = 0)))
            assertEquals(Ignored, tick(session, position = 0, duration = 0, ended = false))
            val shown = tick(session, position = 0, duration = 0, ended = true) as QuestionShown
            assertNull(shown.question.remainingMs)
            assertEquals(Ignored, tick(session, position = 0, duration = 0, now = Long.MAX_VALUE))
            assertTrue(session.select(0, 0) is Target)
        }
        val session = ready(node(question(type = 4, start = 0, duration = 0)))
        assertTrue(tick(session, position = 0, duration = 0, ended = false) is QuestionShown)
        assertNull(session.state.question!!.remainingMs)
    }

    @Test
    fun normalAndRandomVariablesKeepExistingValuesAcrossEdgesAndAddNewIds() {
        val vars = listOf(variable("x", 5.0), variable("random", 7.0, type = 2), InteractionHiddenVar(id = "legacy", value = 3.0, type = 1))
        val session = ready(node(question(choices = listOf(choice(action = "x=x-1;random=random+1")))), variables = vars)
        assertEquals(3.0, session.state.variables["legacy"])
        assertFalse(session.state.variables.containsKey("fallback"))
        tick(session)
        val target = session.select(0, 0) as Target
        val next = node(edge = 2).copy(hiddenVars = listOf(variable("x", 100.0), variable("random", 99.0, type = 2), variable("new", 6.0)))
        val media = session.targetReceived(target.token, next) as PrepareMedia
        assertEquals(5.0, session.state.variables["x"])
        assertFalse(session.state.variables.containsKey("new"))
        session.mediaPrepared(media.token)
        assertEquals(mapOf("x" to 4.0, "random" to 8.0, "legacy" to 3.0, "new" to 6.0), session.state.variables)
        assertEquals(5.0, session.state.visits.first().variables["x"])
    }

    @Test
    fun unsupportedVariableTypesAndSkipOverwriteNeverGuess() {
        for (skip in listOf(1, -1, 2)) {
            val result = InteractionSession().load(7, node().copy(hiddenVars = listOf(variable("x", 1.0).copy(skipOverwrite = skip))), 10)
            assertEquals(ErrorKind.UNSUPPORTED, (result as Error).problem.kind)
        }
        for (type in listOf(0, 3, 127)) {
            val result = InteractionSession().load(7, node().copy(hiddenVars = listOf(variable("x", 1.0, type))), 10)
            assertEquals(ErrorKind.UNSUPPORTED, (result as Error).problem.kind)
        }
        val session = ready(node(question()))
        tick(session)
        val target = session.select(0, 0) as Target
        val result = session.targetReceived(target.token, node(edge = 2).copy(hiddenVars = listOf(variable("x", 0.0).copy(skipOverwrite = 1))))
        assertEquals(ErrorKind.UNSUPPORTED, (result as Error).problem.kind)
        assertEquals(1L, session.state.edgeId)
        assertTrue(session.retry() is Target)
    }

    @Test
    fun invalidDataAndResourceLimitsFailExplicitly() {
        val invalid = listOf(
            node(edge = 0), node(question(choices = listOf(choice(edge = 0)))),
            node(question(choices = listOf(choice(cid = -1)))),
            node().copy(storyList = listOf(InteractionStoryNode(edgeId = 0, cid = 10))),
            node().copy(hiddenVars = listOf(variable("x", Double.NaN))),
            node().copy(hiddenVars = listOf(variable("x", Double.POSITIVE_INFINITY))),
            node().copy(hiddenVars = listOf(variable("", 0.0).copy(id = "")))
        )
        for (value in invalid) assertEquals(ErrorKind.INVALID_DATA, (InteractionSession().load(7, value, 10) as Error).problem.kind)
        val huge = listOf(
            node(*Array(InteractionSession.MAX_QUESTIONS + 1) { question() }),
            node(question(choices = List(InteractionSession.MAX_CHOICES + 1) { choice() })),
            node().copy(hiddenVars = List(InteractionSession.MAX_VARIABLES + 1) { variable("x$it", 0.0) }),
            node().copy(storyList = List(InteractionSession.MAX_STORY_NODES + 1) { InteractionStoryNode(edgeId = 1, cid = 10) }),
            node(question(choices = listOf(choice(action = "1".repeat(InteractionExpression.MAX_SOURCE_LENGTH + 1))))),
            node(*Array(9) { question(choices = List(InteractionSession.MAX_CHOICES) { choice() }) }),
            node(question(choices = List(17) { choice(condition = "1".repeat(InteractionExpression.MAX_SOURCE_LENGTH)) }))
        )
        for (value in huge) assertEquals(ErrorKind.LIMIT_EXCEEDED, (InteractionSession().load(7, value, 10) as Error).problem.kind)
        assertEquals(ErrorKind.INVALID_DATA, (InteractionSession().load(7, node(), 0) as Error).problem.kind)
        for (type in listOf(3, 127)) assertEquals(ErrorKind.UNSUPPORTED, (InteractionSession().load(7, node(question(type = type)), 10) as Error).problem.kind)
    }

    @Test
    fun automaticSelfLoopHasBoundedRuntimeProtection() {
        val source = node(question(type = 0, choices = listOf(choice(edge = 1, cid = 10, default = 1))))
        val session = ready(source)
        repeat(InteractionSession.MAX_AUTOMATIC_TRANSITIONS) {
            val target = tick(session) as Target
            val media = session.targetReceived(target.token, source) as PrepareMedia
            session.mediaPrepared(media.token)
        }
        assertEquals(ErrorKind.LIMIT_EXCEEDED, (tick(session) as Error).problem.kind)
        assertEquals(Ignored, tick(session))
        val restart = session.restart() as PrepareMedia
        session.mediaPrepared(restart.token)
        assertTrue(tick(session) is Target)
    }

    @Test
    fun boundedHistoryAndPublicCheckpointCanBeReconstructedForAStore() {
        val source = node(question(choices = listOf(choice(edge = 1, cid = 10, action = "x=x+1"))))
        val session = ready(source)
        repeat(InteractionSession.MAX_VISITS + 3) {
            tick(session)
            val target = session.select(0, 0) as Target
            val media = session.targetReceived(target.token, source) as PrepareMedia
            session.mediaPrepared(media.token)
        }
        assertEquals(InteractionSession.MAX_VISITS, session.state.visits.size)
        assertTrue(session.state.visits.first().visitId > 1)
        val saved = session.checkpoint()!!
        val reconstructed = InteractionSession.Checkpoint(saved.graphVersion, saved.edgeId, saved.cid, saved.variables.toMap(), saved.root.copy(), saved.visits.map { it.copy(variables = it.variables.toMap()) })
        val restored = InteractionSession()
        val media = restored.load(7, source, 10, reconstructed) as PrepareMedia
        restored.mediaPrepared(media.token)
        assertEquals(saved.variables, restored.state.variables)
        assertTrue(restored.state.visitId!! > saved.visits.last().visitId)
        assertEquals(InteractionSession.MAX_VISITS, restored.state.visits.size)
        val mismatch = restored.load(8, source, 10, reconstructed) as Error
        assertEquals(ErrorKind.INVALID_DATA, mismatch.problem.kind)
    }

    @Test
    fun exposedSnapshotsDoNotAliasCallerCollectionsAndCannotBeMutated() {
        val choices = mutableListOf(choice())
        val source = node(question(choices = choices))
        val session = ready(source, variables = listOf(variable("x", 1.0)))
        choices.clear()
        tick(session)
        assertEquals(listOf(0), session.state.question!!.visibleChoiceIndices)
        val snapshot = session.state
        assertMutationRejected { (snapshot.variables as MutableMap)["x"] = 20.0 }
        assertMutationRejected { (snapshot.visits as MutableList).clear() }
        assertMutationRejected { (snapshot.question!!.visibleChoiceIndices as MutableList).clear() }
        assertMutationRejected { (snapshot.completedQuestionIndices as MutableSet).clear() }
        val target = session.select(0, 0) as Target
        assertMutationRejected { (session.state.transition!!.candidateVariables as MutableMap)["x"] = 30.0 }
        val media = session.targetReceived(target.token, node(edge = 2)) as PrepareMedia
        session.mediaPrepared(media.token)
        assertEquals(1L, snapshot.edgeId)
        assertEquals(2L, session.state.edgeId)
    }

    @Test
    fun knownJumpActionIsCheckedAgainstTargetAndUnknownProtocolsAreUnsupported() {
        val valid = ready(node(question(choices = listOf(choice().copy(platformAction = "JUMP 2 20")))))
        tick(valid)
        assertTrue(valid.select(0, 0) is Target)
        for (action in listOf("JUMP 3 20", "JUMP 2 21", "JUMP 9999999999999999999999 20")) {
            val session = ready(node(question(choices = listOf(choice().copy(platformAction = action)))))
            tick(session)
            assertEquals(ErrorKind.INVALID_DATA, (session.select(0, 0) as Error).problem.kind)
        }
        for (action in listOf("OPEN 2 20", "JUMP 2 20;evil()", "JUMP 2")) {
            val session = ready(node(question(choices = listOf(choice().copy(platformAction = action)))))
            tick(session)
            assertEquals(ErrorKind.UNSUPPORTED, (session.select(0, 0) as Error).problem.kind)
        }
    }

    @Test
    fun falseConditionDoesNotExcludeOfficialTypeZeroDefault() {
        val session = ready(node(question(type = 0, choices = listOf(choice(), choice(edge = 3, condition = "0", default = 1)))))
        assertEquals(3L, (tick(session) as Target).edgeId)
    }

    @Test
    fun oldVisitAndGenerationClicksCannotSelectANewVisitOrRestartedSession() {
        val source = node(question(choices = listOf(choice(edge = 1, cid = 10))))
        val session = ready(source)
        val previousVisit = session.state.visitId!!
        val previousGeneration = session.state.generation
        tick(session)
        val target = session.select(0, 0, previousVisit, previousGeneration) as Target
        val media = session.targetReceived(target.token, source) as PrepareMedia
        session.mediaPrepared(media.token)
        tick(session)
        assertEquals(Ignored, session.select(0, 0, previousVisit, previousGeneration))
        assertNull(session.state.transition)
        assertTrue(session.select(0, 0, session.state.visitId, session.state.generation) is Target)
        val restarted = session.restart() as PrepareMedia
        session.mediaPrepared(restarted.token)
        assertEquals(previousVisit, session.state.visitId)
        assertTrue(session.state.generation > previousGeneration)
        assertEquals(restarted.token.generation, session.state.generation)
        tick(session)
        assertEquals(Ignored, session.select(0, 0, previousVisit, previousGeneration))
        assertNull(session.state.transition)
        assertTrue(session.select(0, 0, session.state.visitId, session.state.generation) is Target)
    }

    @Test
    fun variableGrowthDuringFilteringIsBoundedAndAtomic() {
        val session = ready(
            node(question(choices = listOf(choice(condition = "extra=1")))),
            List(InteractionSession.MAX_VARIABLES) { variable("x$it", 0.0) }
        )
        assertEquals(ErrorKind.LIMIT_EXCEEDED, (tick(session) as Error).problem.kind)
        assertEquals(InteractionSession.MAX_VARIABLES, session.state.variables.size)
        assertFalse(session.state.variables.containsKey("extra"))
    }

    @Test
    fun corruptCheckpointCannotPoisonSubsequentFreshLoads() {
        val base = InteractionSession.Checkpoint(7, 1, 10, mapOf("x" to 1.0), InteractionSession.RootIdentity(1, 10))
        val checkpoints = listOf(
            base.copy(variables = mapOf("x" to Double.NaN)),
            base.copy(cid = 20),
            base.copy(root = InteractionSession.RootIdentity(0, 10)),
            base.copy(visits = listOf(InteractionSession.Visit(Long.MAX_VALUE, 1, 10, emptyMap()))),
            base.copy(visits = listOf(InteractionSession.Visit(Long.MAX_VALUE - 1, 1, 10, emptyMap()))),
            base.copy(visits = listOf(InteractionSession.Visit(2, 1, 10, emptyMap()), InteractionSession.Visit(1, 1, 10, emptyMap()))),
            base.copy(visits = listOf(InteractionSession.Visit(1, 1, 10, mapOf("x" to Double.NEGATIVE_INFINITY))))
        )
        val session = InteractionSession()
        for (checkpoint in checkpoints) {
            assertEquals(ErrorKind.INVALID_DATA, (session.load(7, node(), 10, checkpoint) as Error).problem.kind)
            val media = session.load(7, node(), 10) as PrepareMedia
            assertTrue(session.mediaPrepared(media.token) is Committed)
            assertEquals(1L, session.state.visitId)
        }
    }

    @Test
    fun visitIdCeilingNeverExportsAnUnreadableCheckpointAndRestartRecovers() {
        val source = node(question())
        val saved = InteractionSession.Checkpoint(
            7, 1, 10, emptyMap(), InteractionSession.RootIdentity(1, 10),
            listOf(InteractionSession.Visit(InteractionSession.MAX_VISIT_ID - 1, 1, 10, emptyMap()))
        )
        val session = InteractionSession()
        val initial = session.load(7, source, 10, saved) as PrepareMedia
        assertTrue(session.mediaPrepared(initial.token) is Committed)
        assertEquals(InteractionSession.MAX_VISIT_ID, session.state.visitId)
        tick(session)
        val target = session.select(0, 0) as Target
        val media = session.targetReceived(target.token, node(edge = 2)) as PrepareMedia
        assertEquals(ErrorKind.LIMIT_EXCEEDED, (session.mediaPrepared(media.token) as Error).problem.kind)
        assertEquals(1L, session.state.edgeId)
        val exported = session.checkpoint()!!
        val restored = InteractionSession()
        val restoredMedia = restored.load(7, source, 10, exported) as PrepareMedia
        assertTrue(restored.mediaPrepared(restoredMedia.token) is Committed)
        assertEquals(3L, restored.state.visitId)
        assertEquals(listOf(1L, 2L, 3L), restored.state.visits.map { it.visitId })
        val exportedAgain = restored.checkpoint()!!
        val reloaded = restored.load(7, source, 10, exportedAgain) as PrepareMedia
        assertTrue(restored.mediaPrepared(reloaded.token) is Committed)
        val restart = session.restart() as PrepareMedia
        assertTrue(restart.token.generation > media.token.generation)
        assertEquals(Ignored, session.mediaPrepared(media.token))
        assertEquals(Ignored, session.transitionFailed(media.token, "stale failure"))
        assertTrue(session.mediaPrepared(restart.token) is Committed)
        assertEquals(1L, session.state.visitId)
        session.reset()
        val fresh = session.load(7, source, 10) as PrepareMedia
        assertTrue(session.mediaPrepared(fresh.token) is Committed)
        assertEquals(1L, session.state.visitId)
    }

    @Test
    fun typeFourNaturalPositionAndWallProgressSelectDefaultAtAndBeyondWindowEnd() {
        for (end in listOf(2_000L, 2_100L)) {
            val session = ready(node(question(type = 4, start = 1_000, duration = 1_000, choices = listOf(choice(default = 1)))))
            assertTrue(tick(session, position = 1_000, now = 5_000, ended = false) is QuestionShown)
            assertEquals(Ignored, tick(session, position = 1_500, now = 5_500, ended = false))
            assertEquals(500L, session.state.question!!.remainingMs)
            assertTrue(tick(session, position = end, now = 5_000 + end - 1_000, ended = false) is Target)
            assertEquals(Ignored, tick(session, position = end, now = 99_000, ended = false))
        }
    }

    @Test
    fun typeFourSeekPastEndCancelsEvenWhenWallTimerHasExpired() {
        val session = ready(node(question(type = 4, start = 1_000, duration = 1_000, choices = listOf(choice(default = 1)))))
        tick(session, position = 1_000, now = 0, ended = false)
        assertEquals(QuestionDismissed, tick(session, position = 2_500, now = 10_000, ended = false, discontinuity = true))
        assertNull(session.state.transition)
        assertNull(session.state.question)
        assertEquals(Ignored, tick(session, position = 1_000, now = 11_000, ended = false))
    }

    @Test
    fun typeFourOutsideWindowWithoutExpiredTimerDoesNotInventDefault() {
        val session = ready(node(question(type = 4, start = 1_000, duration = 1_000, choices = listOf(choice(default = 1)))))
        tick(session, position = 1_500, now = 0, ended = false)
        assertEquals(QuestionDismissed, tick(session, position = 2_000, now = 500, ended = false))
        assertNull(session.state.transition)
    }

    @Test
    fun seekInsideWindowDoesNotChargeUnobservedWallTime() {
        val session = ready(node(question(type = 4, start = 1_000, duration = 2_000, choices = listOf(choice(default = 1)))))
        tick(session, position = 1_000, now = 0, ended = false)
        assertEquals(Ignored, tick(session, position = 1_500, now = 10_000, ended = false, discontinuity = true))
        assertEquals(2_000L, session.state.question!!.remainingMs)
        assertEquals(Ignored, tick(session, position = 2_000, now = 10_500, ended = false))
        assertEquals(1_500L, session.state.question!!.remainingMs)
    }

    @Test
    fun unanswerableUntimedQuestionErrorsInsteadOfHangingOrSelectingLaterQuestion() {
        for (type in 1..2) {
            for (choices in listOf(listOf(choice(condition = "x=9;0")), listOf(choice(hidden = 1, default = 1)), emptyList())) {
                val session = ready(node(
                    question(type = type, choices = choices),
                    question(type = 0, choices = listOf(choice(edge = 3, default = 1)))
                ), variables = listOf(variable("x", 5.0)))
                assertEquals(ErrorKind.INVALID_DATA, (tick(session) as Error).problem.kind)
                assertNull(session.state.question)
                assertNull(session.state.transition)
                assertEquals(5.0, session.state.variables["x"])
                assertEquals(Ignored, tick(session, now = 99_000))
                assertEquals(setOf(0), session.state.completedQuestionIndices)
                val restart = session.restart() as PrepareMedia
                assertTrue(session.mediaPrepared(restart.token) is Committed)
                assertNull(session.state.problem)
            }
        }
    }

    @Test
    fun invisibleTimedQuestionRequiresAnEligibleDefaultAndNeverFallsThroughToNextQuestion() {
        for (choices in listOf(listOf(choice(condition = "0", default = 1)), listOf(choice(hidden = 1)), emptyList())) {
            val session = ready(node(
                question(duration = 1_000, choices = choices),
                question(type = 0, choices = listOf(choice(edge = 3, default = 1)))
            ))
            assertEquals(ErrorKind.INVALID_DATA, (tick(session, position = 9_000, ended = false) as Error).problem.kind)
            assertEquals(Ignored, tick(session, now = 10_000))
            assertNull(session.state.transition)
        }
        val session = ready(node(question(duration = 1_000, choices = listOf(choice(hidden = 1, default = 1)))))
        val shown = tick(session, position = 9_000, ended = false) as QuestionShown
        assertTrue(shown.question.visibleChoiceIndices.isEmpty())
        assertEquals(1_000L, shown.question.remainingMs)
        assertTrue(tick(session, position = 10_000, now = 1_000) is Target)
    }

    @Test
    fun restoringBPreservesRootAAndRestartUsesOnlyOriginalRootVariables() {
        val root = node(question(choices = listOf(choice(action = "x=x-3")))).copy(hiddenVars = listOf(variable("x", 10.0)))
        val next = node(edge = 2).copy(hiddenVars = listOf(variable("x", 100.0), variable("onlyB", 7.0)))
        val original = ready(root)
        tick(original)
        val target = original.select(0, 0) as Target
        val media = original.targetReceived(target.token, next) as PrepareMedia
        original.mediaPrepared(media.token)
        val saved = original.checkpoint()!!
        assertEquals(InteractionSession.RootIdentity(1, 10), saved.root)
        assertEquals(7.0, saved.variables["x"])
        val restored = InteractionSession()
        assertEquals(ErrorKind.INVALID_DATA, (restored.load(7, next, 20, saved) as Error).problem.kind)
        assertEquals(ErrorKind.INVALID_DATA, (restored.load(7, next, 20, saved, rootNode = next) as Error).problem.kind)
        val restoreMedia = restored.load(7, next, 20, saved, rootNode = root) as PrepareMedia
        restored.mediaPrepared(restoreMedia.token)
        assertEquals(2L, restored.state.edgeId)
        assertEquals(7.0, restored.state.variables["x"])
        assertEquals(saved.root, restored.checkpoint()!!.root)
        val restart = restored.restart() as PrepareMedia
        assertEquals(1L, restart.edgeId)
        assertEquals(10L, restart.cid)
        assertEquals(0L, restart.seekToMs)
        assertEquals(Ignored, restored.mediaPrepared(restoreMedia.token))
        restored.mediaPrepared(restart.token)
        assertEquals(1L, restored.state.edgeId)
        assertEquals(mapOf("x" to 10.0), restored.state.variables)
        assertEquals(1, restored.state.visits.size)
    }

    private fun assertMutationRejected(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Snapshot is mutable")
        } catch (_: UnsupportedOperationException) {
        }
    }

    private fun ready(value: InteractionVideo, variables: List<InteractionHiddenVar> = value.hiddenVars): InteractionSession {
        val session = InteractionSession()
        val media = session.load(7, value.copy(hiddenVars = variables), 10) as PrepareMedia
        session.mediaPrepared(media.token)
        return session
    }

    private fun tick(
        session: InteractionSession,
        position: Long = 10_000,
        duration: Long = 10_000,
        ended: Boolean = true,
        now: Long = 0,
        advancing: Boolean = true,
        discontinuity: Boolean = false
    ): InteractionSession.Event = session.observePlayback(position, duration, ended, now, advancing, discontinuity)

    private fun node(vararg questions: InteractionQuestion, edge: Long = 1): InteractionVideo =
        InteractionVideo(edgeId = edge, edges = InteractionEdge(questions = questions.toList()))

    private fun question(
        type: Int = 1,
        duration: Long = -1,
        choices: List<InteractionChoice> = listOf(choice()),
        start: Long? = null,
        pause: Int = 0
    ): InteractionQuestion = InteractionQuestion(type = type, duration = duration, choices = choices, startTime = start, pauseVideo = pause)

    private fun choice(
        edge: Long = 2,
        cid: Long = 20,
        condition: String = "",
        action: String = "",
        default: Int = 0,
        hidden: Int = 0
    ): InteractionChoice = InteractionChoice(id = edge, cid = cid, condition = condition, nativeAction = action, isDefault = default, isHidden = hidden)

    private fun variable(key: String, value: Double, type: Int = 1): InteractionHiddenVar =
        InteractionHiddenVar(id = "fallback", idV2 = key, value = value, type = type, skipOverwrite = 0)
}
