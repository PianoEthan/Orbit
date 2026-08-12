package com.qx.orbit.bili.data.account

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal class AccountRegistry(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun accountState(): AccountState {
        val accounts = readAccounts()
        val activeMid = preferences.getLong(KEY_ACTIVE_MID, 0L)
        return AccountState(
            accounts = accounts.map { account ->
                AccountSummary(
                    mid = account.mid,
                    name = account.name,
                    avatarUrl = account.avatarUrl,
                )
            },
            activeMid = activeMid,
        )
    }

    @Synchronized
    fun activeAccount(): StoredAccount? {
        val activeMid = preferences.getLong(KEY_ACTIVE_MID, 0L)
        return readAccounts().firstOrNull { it.mid == activeMid }
    }

    @Synchronized
    fun migrateLegacy(mid: Long, cookie: String) {
        if (mid <= 0L || cookie.isBlank()) return
        val current = readAccounts()
        val existingIndex = current.indexOfFirst { it.mid == mid }
        if (existingIndex >= 0) {
            val updated = current.toMutableList()
            updated[existingIndex] = updated[existingIndex].copy(cookie = cookie)
            writeAccounts(updated)
            preferences.edit().putLong(KEY_ACTIVE_MID, mid).apply()
            return
        }
        upsert(
            StoredAccount(
                mid = mid,
                name = defaultName(mid),
                avatarUrl = "",
                cookie = cookie,
            ),
            select = true,
        )
    }

    @Synchronized
    fun upsert(account: StoredAccount, select: Boolean) {
        val current = readAccounts().toMutableList()
        val index = current.indexOfFirst { it.mid == account.mid }
        if (index >= 0) {
            val existing = current[index]
            current[index] = account.copy(
                name = account.name.ifBlank { existing.name },
                avatarUrl = account.avatarUrl.ifBlank { existing.avatarUrl },
                cookie = account.cookie.ifBlank { existing.cookie },
            )
        } else {
            current += account.copy(name = account.name.ifBlank { defaultName(account.mid) })
        }
        writeAccounts(current)
        if (select) {
            preferences.edit().putLong(KEY_ACTIVE_MID, account.mid).apply()
        }
    }

    @Synchronized
    fun updateCookie(mid: Long, cookie: String) {
        val current = readAccounts().toMutableList()
        val index = current.indexOfFirst { it.mid == mid }
        if (index < 0) return
        current[index] = current[index].copy(cookie = cookie)
        writeAccounts(current)
    }

    @Synchronized
    fun select(mid: Long): StoredAccount? {
        val account = readAccounts().firstOrNull { it.mid == mid } ?: return null
        preferences.edit().putLong(KEY_ACTIVE_MID, mid).apply()
        return account
    }

    @Synchronized
    fun remove(mid: Long): StoredAccount? {
        val remaining = readAccounts().filterNot { it.mid == mid }
        writeAccounts(remaining)
        val next = remaining.firstOrNull()
        preferences.edit().putLong(KEY_ACTIVE_MID, next?.mid ?: 0L).apply()
        return next
    }

    @Synchronized
    fun beginLogin(cookie: String, mid: Long) {
        if (isLoginPending()) return
        preferences.edit()
            .putBoolean(KEY_LOGIN_PENDING, true)
            .putString(KEY_BACKUP_COOKIE, cookie)
            .putLong(KEY_BACKUP_MID, mid)
            .apply()
    }

    @Synchronized
    fun finishLogin() {
        clearPendingLogin()
    }

    @Synchronized
    fun cancelLogin(): AccountSession? {
        if (!isLoginPending()) return null
        val session = AccountSession(
            cookie = preferences.getString(KEY_BACKUP_COOKIE, "").orEmpty(),
            mid = preferences.getLong(KEY_BACKUP_MID, 0L),
        )
        clearPendingLogin()
        return session
    }

    @Synchronized
    fun recoverInterruptedLogin(): AccountSession? = cancelLogin()

    @Synchronized
    fun isLoginPending(): Boolean = preferences.getBoolean(KEY_LOGIN_PENDING, false)

    private fun clearPendingLogin() {
        preferences.edit()
            .remove(KEY_LOGIN_PENDING)
            .remove(KEY_BACKUP_COOKIE)
            .remove(KEY_BACKUP_MID)
            .apply()
    }

    private fun readAccounts(): List<StoredAccount> {
        val raw = preferences.getString(KEY_ACCOUNTS, "").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                item.toStoredAccount()?.let(::add)
            }
        }
    }

    private fun writeAccounts(accounts: List<StoredAccount>) {
        val array = JSONArray()
        accounts.forEach { account -> array.put(account.toJson()) }
        preferences.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    private fun StoredAccount.toJson() = JSONObject().apply {
        put(KEY_MID, mid)
        put(KEY_NAME, name)
        put(KEY_AVATAR_URL, avatarUrl)
        put(KEY_COOKIE, cookie)
    }

    private fun JSONObject.toStoredAccount(): StoredAccount? {
        val mid = optLong(KEY_MID, 0L)
        if (mid <= 0L) return null
        return StoredAccount(
            mid = mid,
            name = optString(KEY_NAME).ifBlank { defaultName(mid) },
            avatarUrl = optString(KEY_AVATAR_URL),
            cookie = optString(KEY_COOKIE),
        )
    }

    private companion object {
        private const val PREFERENCES_NAME = "orbit_accounts"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ACTIVE_MID = "active_mid"
        private const val KEY_LOGIN_PENDING = "login_pending"
        private const val KEY_BACKUP_COOKIE = "backup_cookie"
        private const val KEY_BACKUP_MID = "backup_mid"
        private const val KEY_MID = "mid"
        private const val KEY_NAME = "name"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_COOKIE = "cookie"

        private fun defaultName(mid: Long) = "用户 $mid"
    }
}
