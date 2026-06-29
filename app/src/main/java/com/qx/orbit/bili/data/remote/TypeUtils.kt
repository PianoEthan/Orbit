package com.qx.orbit.bili.data.remote

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

object TypeUtils {
    fun newParameterizedTypeWithOwner(
        ownerType: Type?,
        rawType: Type,
        vararg typeArguments: Type
    ): ParameterizedType {
        return object : ParameterizedType {
            override fun getActualTypeArguments(): Array<out Type> = typeArguments
            override fun getRawType(): Type = rawType
            override fun getOwnerType(): Type? = ownerType
        }
    }
}
