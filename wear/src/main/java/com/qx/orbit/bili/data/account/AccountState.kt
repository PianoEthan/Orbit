package com.qx.orbit.bili.data.account

data class AccountSummary(
    val mid: Long,
    val name: String,
    val avatarUrl: String,
)

data class AccountState(
    val accounts: List<AccountSummary> = emptyList(),
    val activeMid: Long = 0L,
)

internal data class StoredAccount(
    val mid: Long,
    val name: String,
    val avatarUrl: String,
    val cookie: String,
)

internal data class AccountSession(
    val cookie: String,
    val mid: Long,
)
