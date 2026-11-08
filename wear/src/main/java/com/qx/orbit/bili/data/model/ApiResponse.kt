package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("message") val message: String? = null,
    @SerializedName(value = "data", alternate = ["result"]) val data: T? = null
) {
    val isSuccess: Boolean get() = code == 0
}
