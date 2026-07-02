package com.qx.orbit.bili.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

import android.os.Environment

object ShizukuUtils {
    fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false // On older versions, this permission doesn't exist
        }
    }
    
    fun getShizukuVersionName(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            packageInfo.versionName
        } catch (_: Exception) {
            null
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun isShizukuAuthorized(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun openShizukuManager(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SHIZUKU", "Failed to open Shizuku: ${e.message}")
            false
        }
    }

    fun grantManageExternalStorage(context: Context): Boolean {
        if (!isShizukuAvailable() || !isShizukuAuthorized()) {
            return false
        }
        return try {
            val cmd = arrayOf("appops", "set", context.packageName, "MANAGE_EXTERNAL_STORAGE", "allow")
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, cmd, null, null) as ShizukuRemoteProcess
            process.waitFor()
            process.destroy()
            true
        } catch (e: Exception) {
            Log.e("SHIZUKU_GRANT", "Error granting MANAGE_EXTERNAL_STORAGE: ${e.message}")
            false
        }
    }
}
