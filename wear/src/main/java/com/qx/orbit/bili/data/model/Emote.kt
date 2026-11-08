package com.qx.orbit.bili.data.model
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Emote(
    val id: Int = 0,
    val packageId: Int = 0,
    val name: String = "",
    val alias: String = "",
    val url: String = "",
    val size: Int = 0
) : Parcelable

@Parcelize
data class EmotePackage(
    val id: Int = 0,
    val text: String = "",
    val url: String = "",
    val type: Int = 0,
    val attr: Int = 0,
    val size: Int = 0,
    val item_id: Int = 0,
    val emotes: List<Emote> = emptyList(),
    val permanent: Boolean = false
) : Parcelable
