package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreChatHistoryPage
import com.qx.orbit.bili.core.api.CoreChatMessage
import com.qx.orbit.bili.core.api.CoreChatSession
import com.qx.orbit.bili.core.api.CoreChatUser
import com.qx.orbit.bili.core.api.CoreNotice
import com.qx.orbit.bili.core.api.CoreNoticeKind
import com.qx.orbit.bili.core.api.CoreNoticePage
import com.qx.orbit.bili.core.api.CoreUnreadCount
import com.qx.orbit.bili.core.api.MessageGateway
import com.qx.orbit.bili.core.api.PrivateMsgGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionMessageViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeMessages(
        var unread: CoreUnreadCount = CoreUnreadCount(at = 1, like = 2, reply = 3, system = 4),
        val noticePages: MutableMap<CoreNoticeKind, ArrayDeque<CoreNoticePage>> = mutableMapOf(),
    ) : MessageGateway {
        override suspend fun loadUnread(cookie: String) = unread
        override suspend fun loadNotices(kind: CoreNoticeKind, id: Long, time: Long, cookie: String) =
            noticePages[kind]?.removeFirstOrNull() ?: CoreNoticePage(emptyList(), isEnd = true)
        override suspend fun loadSystemMessages(cookie: String, csrf: String) = emptyList<CoreNotice>()
    }

    private class FakePrivate(
        var sessions: List<CoreChatSession> = emptyList(),
        var users: List<CoreChatUser> = emptyList(),
        val historyPages: ArrayDeque<CoreChatHistoryPage> = ArrayDeque(),
        val acks: MutableList<Long> = mutableListOf(),
        var sendOk: Boolean? = null,
    ) : PrivateMsgGateway {
        val sent = mutableListOf<Pair<Long, String>>()
        override suspend fun loadSessions(size: Int, cookie: String) = sessions
        override suspend fun loadHistory(talkerId: Long, size: Int, beginSeqno: Long, endSeqno: Long, cookie: String) =
            historyPages.removeFirstOrNull() ?: CoreChatHistoryPage(emptyList(), hasMore = false)
        override suspend fun loadUsers(uids: List<Long>, cookie: String) = users
        override suspend fun sendText(senderUid: Long, receiverUid: Long, text: String, cookie: String, csrf: String): Boolean {
            sent += receiverUid to text
            return sendOk ?: error("send unconfigured")
        }
        override suspend fun updateAck(talkerId: Long, sessionType: Int, ackSeqno: Long, cookie: String, csrf: String) {
            acks += ackSeqno
        }
    }

    private fun message(uid: Long, seqno: Long, seconds: Long, text: String = "m$seqno") =
        CoreChatMessage(uid, 1, text, seconds, seqno)

    @Test fun centerLoadsUnreadThenSessionsSortedWithNames() = runTest(dispatcher) {
        val private = FakePrivate(
            sessions = listOf(
                CoreChatSession(2, unread = 0, sessionType = 1, lastMessage = message(2, 5, 100)),
                CoreChatSession(1, unread = 3, sessionType = 1, lastMessage = message(1, 9, 300)),
            ),
            users = listOf(CoreChatUser(1, "甲", "https://f/1"), CoreChatUser(2, "乙", "https://f/2")),
        )
        val model = VisionMessageCenterViewModel(FakeMessages(), private)
        model.refresh()
        advanceUntilIdle()
        val state = model.state.value
        assertEquals(10, state.unread.total)
        assertEquals(listOf(1L, 2L), state.sessions.map { it.talkerId })
        assertEquals("甲", state.sessionUsers[1L]?.name)
    }

    @Test fun switchingNoticeTabsPaginatesWithCursorAndKeepsSeparateCursors() = runTest(dispatcher) {
        val messages = FakeMessages(noticePages = mutableMapOf(
            CoreNoticeKind.REPLY to ArrayDeque(listOf(
                CoreNoticePage(listOf(CoreNotice(1, emptyList(), 0, "回复一")), isEnd = false, nextId = 1, nextTime = 10),
                CoreNoticePage(listOf(CoreNotice(2, emptyList(), 0, "回复二")), isEnd = true),
            )),
            CoreNoticeKind.LIKE to ArrayDeque(listOf(
                CoreNoticePage(listOf(CoreNotice(7, emptyList(), 0, "点赞")), isEnd = true),
            )),
        ))
        val model = VisionMessageCenterViewModel(messages, FakePrivate())
        model.refresh()
        advanceUntilIdle()
        model.selectTab(VisionMessageTab.REPLY)
        advanceUntilIdle()
        assertEquals(listOf("回复一"), model.state.value.notices.map { it.content })
        assertTrue(model.state.value.hasMore)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("回复一", "回复二"), model.state.value.notices.map { it.content })
        model.selectTab(VisionMessageTab.LIKE)
        advanceUntilIdle()
        assertEquals(listOf("点赞"), model.state.value.notices.map { it.content })
        assertFalse(model.state.value.hasMore)
    }

    @Test fun chatOpenSortsAscendingAndLoadOlderMergesWithoutDuplicates() = runTest(dispatcher) {
        val private = FakePrivate(historyPages = ArrayDeque(listOf(
            CoreChatHistoryPage(listOf(message(1, 20, 200), message(2, 21, 210)), hasMore = true),
            CoreChatHistoryPage(listOf(message(1, 10, 100), message(1, 20, 200)), hasMore = false),
        )))
        val model = VisionPrivateChatViewModel(private)
        model.open(2, 1)
        advanceUntilIdle()
        assertEquals(listOf(20L, 21L), model.state.value.messages.map { it.seqno })
        assertTrue(model.state.value.hasOlder)
        model.loadOlder()
        advanceUntilIdle()
        assertEquals(listOf(10L, 20L, 21L), model.state.value.messages.map { it.seqno })
        assertTrue(model.state.value.messages.map { it.timestamp } == model.state.value.messages.map { it.timestamp }.sorted())
        assertNull(model.state.value.error)
    }

    @Test fun sendRequiresLoginAndReportsFailureWithoutAppending() = runTest(dispatcher) {
        val private = FakePrivate(historyPages = ArrayDeque(listOf(CoreChatHistoryPage(listOf(message(2, 1, 10)), hasMore = false))))
        val model = VisionPrivateChatViewModel(private)
        model.open(2, 1)
        advanceUntilIdle()
        model.send("你好")
        advanceUntilIdle()
        // 测试环境未登录：应拒绝发送且不追加本地消息
        assertEquals("请先登录", model.state.value.sendError)
        assertEquals(1, model.state.value.messages.size)
        assertTrue(private.sent.isEmpty())
    }
}
