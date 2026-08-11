package com.qx.orbit.bili.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.qx.orbit.bili.data.account.AccountRegistry
import com.qx.orbit.bili.data.account.AccountSession
import com.qx.orbit.bili.data.account.AccountState
import com.qx.orbit.bili.data.account.StoredAccount
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CookieManager {
    private lateinit var prefs: SharedPreferences
    private lateinit var accountRegistry: AccountRegistry
    private var initialized = false
    private val sessionGeneration = AtomicLong(0L)
    private val _accountState = MutableStateFlow(AccountState())
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.getSharedPreferences("orbit_cookies", Context.MODE_PRIVATE)
        accountRegistry = AccountRegistry(context)
        accountRegistry.recoverInterruptedLogin()?.let(::writeSession)
        val cookie = getCookie()
        val mid = getMid()
        if (isAuthenticated(cookie, mid)) {
            accountRegistry.migrateLegacy(mid, cookie)
        }
        refreshAccountState()
        initialized = true
    }

    fun getCookie(): String = prefs.getString("cookie", "") ?: ""

    @Synchronized
    fun setCookie(cookie: String) {
        prefs.edit().putString("cookie", cookie).apply()
        if (cookie.isNotBlank() && !accountRegistry.isLoginPending()) {
            val mid = getMid()
            if (accountRegistry.activeAccount()?.mid == mid) {
                accountRegistry.updateCookie(mid, cookie)
            }
        }
    }

    fun getCsrf(): String = getInfoFromCookie("bili_jct")

    fun getMid(): Long {
        val stored = prefs.getLong("mid", 0)
        if (stored > 0) return stored
        val fromCookie = getInfoFromCookie("DedeUserID").toLongOrNull() ?: 0
        if (fromCookie > 0) prefs.edit().putLong("mid", fromCookie).apply()
        return fromCookie
    }

    @Synchronized
    fun setMid(mid: Long) {
        prefs.edit().putLong("mid", mid).apply()
        if (!accountRegistry.isLoginPending() && accountRegistry.activeAccount()?.mid == mid) {
            accountRegistry.updateCookie(mid, getCookie())
        }
    }

    fun getRefreshToken(): String {
        val token = getInfoFromCookie("refresh_token")
        if (token.isNotEmpty()) return token
        return getInfoFromCookie("ac_time_value")
    }

    fun getInfoFromCookie(name: String): String {
        val cookie = getCookie()
        cookie.split("; ").forEach { part ->
            if (part.startsWith("$name=")) {
                return part.substring(name.length + 1)
            }
        }
        return ""
    }

    fun putCookie(key: String, value: String) {
        val cookies = mutableMapOf<String, String>()
        getCookie().split("; ").filter { it.contains("=") }.forEach { part ->
            val eqIdx = part.indexOf("=")
            cookies[part.substring(0, eqIdx)] = part.substring(eqIdx + 1)
        }
        cookies[key] = value
        setCookie(cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }

    fun clearCookie() {
        sessionGeneration.incrementAndGet()
        prefs.edit().remove("cookie").remove("mid").apply()
    }

    @Synchronized
    fun beginAccountLogin() {
        if (accountRegistry.isLoginPending()) return
        accountRegistry.beginLogin(getCookie(), getMid())
        sessionGeneration.incrementAndGet()
        prefs.edit().remove("cookie").remove("mid").apply()
    }

    @Synchronized
    fun cancelAccountLogin() {
        val previousSession = accountRegistry.cancelLogin() ?: return
        sessionGeneration.incrementAndGet()
        writeSession(previousSession)
    }

    @Synchronized
    fun saveCurrentAccount(mid: Long, name: String, avatarUrl: String) {
        val cookie = getCookie()
        if (mid <= 0L || cookie.isBlank()) return
        val accountChanged = accountRegistry.isLoginPending() ||
            accountRegistry.activeAccount()?.mid != mid
        if (accountChanged) {
            sessionGeneration.incrementAndGet()
        }
        prefs.edit().putLong("mid", mid).apply()
        accountRegistry.upsert(
            account = StoredAccount(
                mid = mid,
                name = name,
                avatarUrl = avatarUrl,
                cookie = cookie,
            ),
            select = true,
        )
        accountRegistry.finishLogin()
        refreshAccountState()
    }

    @Synchronized
    fun switchAccount(mid: Long): Boolean {
        val account = accountRegistry.select(mid) ?: return false
        if (getMid() == account.mid && getCookie() == account.cookie) return true
        sessionGeneration.incrementAndGet()
        writeSession(account.cookie, account.mid)
        refreshAccountState()
        return true
    }

    @Synchronized
    fun removeCurrentAccount() {
        val activeMid = accountRegistry.activeAccount()?.mid ?: getMid()
        val nextAccount = if (activeMid > 0L) accountRegistry.remove(activeMid) else null
        sessionGeneration.incrementAndGet()
        if (nextAccount == null) {
            prefs.edit().remove("cookie").remove("mid").apply()
        } else {
            writeSession(nextAccount.cookie, nextAccount.mid)
        }
        refreshAccountState()
    }

    fun currentSessionGeneration(): Long = sessionGeneration.get()

    fun isCookieFromCurrentSession(cookie: String): Boolean {
        val currentMid = getMid()
        val requestMid = getInfoFromCookie(cookie, "DedeUserID").toLongOrNull() ?: 0L
        if (requestMid > 0L) return requestMid == currentMid

        val requestSession = getInfoFromCookie(cookie, "SESSDATA")
        val currentSession = getInfoFromCookie("SESSDATA")
        if (requestSession.isNotEmpty() || currentSession.isNotEmpty()) {
            return requestSession == currentSession
        }
        return currentMid == 0L
    }

    private fun refreshAccountState() {
        _accountState.value = accountRegistry.accountState()
    }

    private fun writeSession(session: AccountSession) {
        writeSession(session.cookie, session.mid)
    }

    private fun writeSession(cookie: String, mid: Long) {
        prefs.edit()
            .putString("cookie", cookie)
            .putLong("mid", mid)
            .apply()
    }

    private fun isAuthenticated(cookie: String, mid: Long): Boolean {
        return mid > 0L && (cookie.contains("SESSDATA=") || cookie.contains("access_token="))
    }

    private fun getInfoFromCookie(cookie: String, name: String): String {
        return cookie.split("; ")
            .firstOrNull { part -> part.startsWith("$name=") }
            ?.substring(name.length + 1)
            .orEmpty()
    }
}
