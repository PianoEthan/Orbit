package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliUserDataGateway
import com.qx.orbit.bili.core.api.CoreFavFolder
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.UserDataGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class FavManagerMode { FOLDERS, MOVE }

internal data class VisionFavManagerState(
    val folders: List<CoreFavFolder> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val mode: FavManagerMode = FavManagerMode.FOLDERS,
    val moveSource: CoreFavFolder? = null,
    val moveVideos: List<CoreVideo> = emptyList(),
    val moveSelected: Set<Long> = emptySet(),
    val moveTarget: Long? = null,
    val moveLoading: Boolean = false,
)

internal class VisionFavManagerViewModel(private val gateway: UserDataGateway = BilibiliUserDataGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionFavManagerState())
    val state = _state.asStateFlow()
    private var cookie = ""
    private var mid = 0L
    private var job: Job? = null

    fun load(session: String, userMid: Long) {
        cookie = session
        mid = userMid
        if (session.isBlank() || userMid <= 0) return
        job?.cancel()
        _state.value = VisionFavManagerState(loading = true)
        job = viewModelScope.launch {
            try {
                val folders = gateway.loadFavFolders(session, userMid)
                _state.value = VisionFavManagerState(folders = folders)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = VisionFavManagerState(error = "收藏夹加载失败，请重试")
            }
        }
    }

    private fun mutate(successMessage: String, block: suspend () -> Boolean) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val ok = block()
                val folders = runCatching { gateway.loadFavFolders(cookie, mid) }.getOrDefault(_state.value.folders)
                _state.value = _state.value.copy(busy = false, folders = folders, message = if (ok) successMessage else "操作失败，请重试")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(busy = false, message = "操作失败，请重试")
            }
        }
    }

    fun create(title: String, intro: String, privacy: Int, csrf: String) =
        mutate("收藏夹已创建") { gateway.createFavFolder(title, intro, privacy, cookie, csrf) }

    fun edit(mediaId: Long, title: String, intro: String, privacy: Int, csrf: String) =
        mutate("收藏夹已更新") { gateway.editFavFolder(mediaId, title, intro, privacy, cookie, csrf) }

    fun delete(mediaId: Long, csrf: String) =
        mutate("收藏夹已删除") { gateway.deleteFavFolder(mediaId, cookie, csrf) }

    fun startMove(folder: CoreFavFolder) {
        _state.value = _state.value.copy(mode = FavManagerMode.MOVE, moveSource = folder, moveVideos = emptyList(), moveSelected = emptySet(), moveTarget = null, moveLoading = true, message = null)
        viewModelScope.launch {
            try {
                val page = gateway.loadFavVideoPage(cookie, folder.id, 1)
                _state.value = _state.value.copy(moveVideos = page.videos, moveLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(moveLoading = false, message = "视频加载失败，请重试")
            }
        }
    }

    fun toggle(aid: Long) {
        val selected = _state.value.moveSelected
        _state.value = _state.value.copy(moveSelected = if (aid in selected) selected - aid else selected + aid)
    }

    fun setTarget(folderId: Long) {
        _state.value = _state.value.copy(moveTarget = folderId)
    }

    fun move(csrf: String) {
        val current = _state.value
        val source = current.moveSource ?: return
        val target = current.moveTarget ?: return
        if (current.busy || current.moveSelected.isEmpty() || target == source.id) return
        _state.value = current.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val ok = gateway.moveFavVideos(current.moveSelected.toList(), source.id, target, cookie, csrf)
                val page = runCatching { gateway.loadFavVideoPage(cookie, source.id, 1) }.getOrNull()
                val folders = runCatching { gateway.loadFavFolders(cookie, mid) }.getOrDefault(current.folders)
                _state.value = _state.value.copy(
                    busy = false,
                    moveVideos = page?.videos ?: current.moveVideos,
                    moveSelected = emptySet(),
                    folders = folders,
                    message = if (ok) "已移动 ${current.moveSelected.size} 个视频" else "移动失败，请重试",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(busy = false, message = "移动失败，请重试")
            }
        }
    }

    fun backToFolders() {
        _state.value = _state.value.copy(mode = FavManagerMode.FOLDERS, moveSource = null, moveVideos = emptyList(), moveSelected = emptySet(), moveTarget = null, message = null)
        load(cookie, mid)
    }

    fun stop() {
        job?.cancel()
        _state.value = _state.value.copy(loading = false, moveLoading = false)
    }
}
