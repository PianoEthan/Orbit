package com.qx.orbit.bili.util

import android.app.DownloadManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qx.orbit.bili.data.api.DanmakuApi
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.DanmakuElem
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.remote.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object VideoDownloadManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val downloads = mutableMapOf<Long, DownloadInfo>()
    private val activeCalls = mutableMapOf<Long, Call>()
    private var nextId = 1L
    private const val TASKS_FILE = "video_download_tasks.json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var appContext: Context? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext
        loadTasks(context)
        scanAndRegisterLocalFiles(context)
    }

    private fun getDownloadDir(bvid: String? = null): File {
        val cacheLocation = SharedPreferencesUtil.getString("cache_location", "internal")
        val baseDir = if (cacheLocation == "external") {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Orbit")
        } else {
            val mediaDir = appContext?.externalMediaDirs?.firstOrNull()
            if (mediaDir != null) File(mediaDir, "Video")
            else File(appContext?.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Video")
        }
        val dir = if (!bvid.isNullOrEmpty()) File(baseDir, bvid) else baseDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun scanAndRegisterLocalFiles(context: Context) {
        try {
            val baseDir = getDownloadDir()
            if (!baseDir.exists()) return

            val activeFiles = downloads.values.map { it.filename }.toSet()

            val filesToScan = mutableListOf<File>()
            val baseFiles = baseDir.listFiles() ?: arrayOf()
            for (f in baseFiles) {
                if (f.isFile) {
                    filesToScan.add(f)
                } else if (f.isDirectory) {
                    f.listFiles()?.let { filesToScan.addAll(it) }
                }
            }

            var changed = false
            for (f in filesToScan) {
                if (f.isFile && (f.name.endsWith(".mp4") || f.name.endsWith(".m4s") || f.name.endsWith(".aac"))) {
                    if (!activeFiles.contains(f.name)) {
                        val id = nextId++
                        val title = f.nameWithoutExtension

                        var durationSecs = 0
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(f.absolutePath)
                            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            retriever.release()
                            durationSecs = ((time?.toLongOrNull() ?: 0L) / 1000).toInt()
                        } catch (e: Exception) { }

                        val info = DownloadInfo(
                            id = id,
                            url = "",
                            title = title,
                            filename = f.name,
                            type = if (f.name.endsWith(".aac") || f.name.endsWith(".m4s")) "AUDIO_AND_SUBTITLE" else "MP4",
                            status = DownloadManager.STATUS_SUCCESSFUL,
                            downloadedBytes = f.length(),
                            totalBytes = f.length(),
                            localUri = f.absolutePath,
                            duration = durationSecs
                        )
                        downloads[id] = info
                        changed = true
                        Log.d("VideoDownloadManager", "Registered local file ${f.name} as download task")
                    }
                }
            }
            if (changed) {
                persistTasks(context)
            }
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "Failed to scan local files", e)
        }
    }

    private fun persistTasks(context: Context) {
        try {
            val json = Gson().toJson(downloads.values.toList())
            File(context.filesDir, TASKS_FILE).writeText(json)
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "Failed to persist tasks", e)
        }
    }

    private fun loadTasks(context: Context) {
        try {
            val file = File(context.filesDir, TASKS_FILE)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<DownloadInfo>>() {}.type
                val list: List<DownloadInfo> = Gson().fromJson(json, type)
                downloads.clear()
                list.forEach {
                    var restored = if (it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING) {
                        it.copy(status = DownloadManager.STATUS_FAILED, reason = -3)
                    } else it

                    if (restored.duration == 0 && restored.localUri != null && File(restored.localUri).exists()) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(restored.localUri)
                            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            retriever.release()
                            val dur = ((time?.toLongOrNull() ?: 0L) / 1000).toInt()
                            if (dur > 0) {
                                restored = restored.copy(duration = dur)
                            }
                        } catch (e: Exception) {}
                    }

                    downloads[it.id] = restored
                    if (it.id >= nextId) nextId = it.id + 1
                }
            }
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "Failed to load tasks", e)
        }
    }

    fun getAllDownloads(): List<DownloadInfo> = downloads.values.toList()

    fun getRunningCount(): Int = downloads.values.count { it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING }

    fun getCompletedCount(): Int = downloads.values.count { it.status == DownloadManager.STATUS_SUCCESSFUL }

    data class DownloadInfo(
        val id: Long,
        val url: String,
        val title: String,
        val filename: String,
        val aid: Long = 0,
        val cid: Long = 0,
        val bvid: String = "",
        val qn: Int = 0,
        val type: String = "MP4", // MP4, AUDIO_AND_SUBTITLE
        val mediaType: Int = PlayerData.TYPE_VIDEO, // Video or Bangumi
        val coverUrl: String = "",
        val duration: Int = 0,
        val status: Int = DownloadManager.STATUS_PENDING,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val localUri: String? = null,
        val reason: Int = 0
    )

    fun enqueue(url: String, title: String, filename: String, context: Context, aid: Long, cid: Long, bvid: String, qn: Int, type: String = "MP4", coverUrl: String = "", duration: Int = 0, mediaType: Int = PlayerData.TYPE_VIDEO): Long {
        val existing = downloads.values.find { it.aid == aid && it.cid == cid && it.type == type && it.qn == qn }

        if (existing != null) {
            if (existing.status == DownloadManager.STATUS_RUNNING ||
                existing.status == DownloadManager.STATUS_PENDING ||
                existing.status == DownloadManager.STATUS_SUCCESSFUL) {
                return existing.id
            }
            activeCalls[existing.id]?.cancel()
            activeCalls.remove(existing.id)
            downloads[existing.id] = existing.copy(status = DownloadManager.STATUS_RUNNING, reason = 0)
            persistTasks(context)
            executeDownload(existing.id, context)
            return existing.id
        }

        val id = nextId++
        val info = DownloadInfo(id, url, title, filename, aid, cid, bvid, qn, type, mediaType, coverUrl, duration, status = DownloadManager.STATUS_RUNNING)
        downloads[id] = info
        persistTasks(context)

        executeDownload(id, context)
        return id
    }

    fun resume(id: Long, context: Context) {
        val existing = downloads[id] ?: return
        if (existing.status != DownloadManager.STATUS_RUNNING && existing.status != DownloadManager.STATUS_PENDING && existing.status != DownloadManager.STATUS_SUCCESSFUL) {
            activeCalls[id]?.cancel()
            activeCalls.remove(id)
            downloads[id] = existing.copy(status = DownloadManager.STATUS_RUNNING, reason = 0)
            persistTasks(context)
            executeDownload(id, context)
        }
    }

    fun pause(id: Long, context: Context) {
        activeCalls[id]?.cancel()
        activeCalls.remove(id)
        val info = downloads[id] ?: return
        if (info.status == DownloadManager.STATUS_RUNNING || info.status == DownloadManager.STATUS_PENDING) {
            downloads[id] = info.copy(status = DownloadManager.STATUS_PAUSED)
            persistTasks(context)
        }
    }

    private fun executeDownload(id: Long, context: Context) {
        val info = downloads[id] ?: return
        val url = info.url
        val filename = info.filename

        if (url.isEmpty()) {
            scope.launch {
                refreshUrlAndRetry(id, context)
            }
            return
        }

        val downloadDir = getDownloadDir(info.bvid)
        val file = File(downloadDir, filename)

        val existingLength = if (file.exists()) file.length() else 0L

        val requestBuilder = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("Referer", "https://www.bilibili.com")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")

        if (existingLength > 0) {
            requestBuilder.header("Range", "bytes=$existingLength-")
        } else {
            requestBuilder.header("Range", "bytes=0-")
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[id] = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                if (activeCalls[id] == call) activeCalls.remove(id)
                downloads[id] = downloads[id]?.copy(status = DownloadManager.STATUS_FAILED, reason = -1) ?: return
                persistTasks(context)
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) return
                
                if (response.code == 403) {
                    if (activeCalls[id] == call) activeCalls.remove(id)
                    // Refresh URL and retry
                    scope.launch {
                        refreshUrlAndRetry(id, context)
                    }
                    return
                }

                if (!response.isSuccessful && response.code != 206) {
                    if (activeCalls[id] == call) activeCalls.remove(id)
                    downloads[id] = downloads[id]?.copy(status = DownloadManager.STATUS_FAILED, reason = response.code) ?: return
                    persistTasks(context)
                    return
                }

                val body = response.body ?: run {
                    if (activeCalls[id] == call) activeCalls.remove(id)
                    downloads[id] = downloads[id]?.copy(status = DownloadManager.STATUS_FAILED) ?: return
                    return
                }

                val isResume = response.code == 206
                val totalLength = if (isResume) {
                    existingLength + body.contentLength()
                } else {
                    body.contentLength()
                }

                downloads[id] = downloads[id]?.copy(totalBytes = totalLength) ?: return

                try {
                    body.byteStream().use { input ->
                        val fos = FileOutputStream(file, isResume)
                        fos.use { output ->
                            val buffer = ByteArray(16384)
                            var read: Int
                            var downloaded = if (isResume) existingLength else 0L
                            var lastUpdate = 0L

                            while (input.read(buffer).also { read = it } != -1) {
                                if (call.isCanceled()) return
                                output.write(buffer, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 200) {
                                    downloads[id] = downloads[id]?.copy(downloadedBytes = downloaded) ?: break
                                    lastUpdate = now
                                }
                            }
                            downloads[id] = downloads[id]?.copy(downloadedBytes = downloaded) ?: return
                        }
                    }
                    
                    if (call.isCanceled()) return
                    if (activeCalls[id] == call) activeCalls.remove(id)
                    
                    downloads[id] = downloads[id]?.copy(status = DownloadManager.STATUS_SUCCESSFUL, localUri = file.absolutePath) ?: return
                    persistTasks(context)

                    // Trigger post download actions (danmaku, subtitle)
                    scope.launch {
                        downloadDanmakuAndSubtitle(id, context)
                    }

                } catch (e: Exception) {
                    if (call.isCanceled()) return
                    if (activeCalls[id] == call) activeCalls.remove(id)
                    downloads[id] = downloads[id]?.copy(status = DownloadManager.STATUS_FAILED, reason = -2) ?: return
                    persistTasks(context)
                }
            }
        })
    }

    private suspend fun refreshUrlAndRetry(id: Long, context: Context) {
        val info = downloads[id] ?: return
        val playerReq = PlayerData(
            aid = info.aid, cid = info.cid, bvid = info.bvid, qn = info.qn, type = info.mediaType
        )
        val refreshed = if (info.type == "AUDIO_AND_SUBTITLE") {
            if (info.mediaType == PlayerData.TYPE_BANGUMI) {
                val dash = PlayerApi.getBangumi(playerReq)
                dash.audioUrl.ifEmpty { dash.videoUrl }
            } else {
                val dash = PlayerApi.getVideoDash(playerReq)
                dash.audioUrl.ifEmpty { dash.videoUrl }
            }
        } else {
            if (info.mediaType == PlayerData.TYPE_BANGUMI) {
                PlayerApi.getBangumi(playerReq, forceMp4 = true).videoUrl
            } else {
                PlayerApi.getVideo(playerReq).videoUrl
            }
        }

        if (refreshed.isNotEmpty() && refreshed != info.url) {
            downloads[id] = info.copy(url = refreshed)
            persistTasks(context)
            executeDownload(id, context)
        } else {
            downloads[id] = info.copy(status = DownloadManager.STATUS_FAILED, reason = 403)
            persistTasks(context)
        }
    }

    private suspend fun downloadDanmakuAndSubtitle(id: Long, context: Context) {
        val info = downloads[id] ?: return
        try {
            val downloadDir = getDownloadDir(info.bvid)
            val danmakuFile = File(downloadDir, "${info.filename}.danmaku.xml")
            if (!danmakuFile.exists()) {
                val segments = ceil(info.duration / 360.0).toInt().coerceAtLeast(1)
                val allElems = mutableListOf<DanmakuElem>()
                for (i in 1..segments) {
                    val seg = DanmakuApi.getVideoDanmakuSegment(info.aid, info.cid, i)
                    if (seg != null) {
                        allElems.addAll(seg.elems)
                    }
                }

                val xmlBuilder = StringBuilder()
                xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<i>\n")
                xmlBuilder.append("<chatserver>chat.bilibili.com</chatserver>\n")
                xmlBuilder.append("<chatid>${info.cid}</chatid>\n")
                xmlBuilder.append("<mission>0</mission>\n")
                xmlBuilder.append("<maxlimit>8000</maxlimit>\n")
                xmlBuilder.append("<state>0</state>\n")
                xmlBuilder.append("<real_name>0</real_name>\n")
                xmlBuilder.append("<source>k-v</source>\n")

                for (elem in allElems) {
                    // p="progress(s),mode,fontsize,color,timestamp,pool,hash,rowId"
                    val p = "${elem.progress / 1000f},${elem.mode},${elem.fontsize},${elem.color},${elem.ctime},0,${elem.midHash},${elem.id}"
                    val content = elem.content.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")
                    xmlBuilder.append("<d p=\"$p\">$content</d>\n")
                }
                xmlBuilder.append("</i>")
                danmakuFile.writeText(xmlBuilder.toString())
            }

            // Download all subtitles (including AI) for all video types
            run {
                val subLinks = PlayerApi.getSubtitleLinks(info.aid, info.cid)
                val baseFilename = info.filename.substringBeforeLast(".")
                for (sub in subLinks) {
                    if (sub.url.isEmpty()) continue
                    try {
                        val lang = sub.lang.ifEmpty { if (sub.isAI) "ai" else "unknown" }
                        val suffix = if (sub.isAI) ".ai.$lang" else ".$lang"
                        val subFile = File(downloadDir, "$baseFilename$suffix.json")
                        if (subFile.exists()) continue
                        val subUrl = sub.url.let { if (it.startsWith("//")) "https:$it" else it }
                        val req = Request.Builder().url(subUrl).build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val json = resp.body?.string()
                            if (json != null) subFile.writeText(json)
                        }
                    } catch (_: Exception) {}
                }
            }

            if (info.coverUrl.isNotEmpty()) {
                val coverFile = File(downloadDir, "${info.filename}.cover.webp")
                if (!coverFile.exists()) {
                    val coverReqUrl = if (info.coverUrl.contains("@")) info.coverUrl else "${info.coverUrl}@480w_270h_1c.webp"
                    val req = Request.Builder().url(coverReqUrl).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(coverFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "Failed to download danmaku/subtitle", e)
        }
    }

    fun remove(context: Context, id: Long) {
        val info = downloads[id]
        if (info != null) {
            activeCalls[id]?.cancel()
            activeCalls.remove(id)

            val downloadDir = getDownloadDir(info.bvid)
            val baseFilename = info.filename.substringBeforeLast(".")

            // 彻底删除该视频的所有关联文件（包括视频、音频、封面、弹幕、多语言字幕等）
            downloadDir.listFiles { _, name -> name.startsWith(baseFilename) || name.startsWith(info.filename) }?.forEach {
                it.delete()
            }

            downloads.remove(id)
            persistTasks(context)

            // 清理空的bvid目录（如果是根目录则不删除）
            if (!info.bvid.isNullOrEmpty() && downloadDir.name == info.bvid && downloadDir.listFiles()?.isEmpty() == true) {
                downloadDir.delete()
            }
        }
    }
}