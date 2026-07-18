package com.qx.orbit.bili.presentation.player

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
internal class PlayerVolumeState(
    private val audioManager: AudioManager,
    private val scope: CoroutineScope,
    private val guardEnabled: Boolean,
    private val playbackStartVolumeLimitPercent: Int?,
    private val onVolumeGuardTriggered: () -> Unit
) {
    private val minVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    } else {
        0
    }
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val volumeSensitivity = (maxVolume - minVolume) * VOLUME_SENSITIVITY_PERCENT
    private val digitalCrownSessionCapVolume = volumeForPercent(
        targetPercent = DIGITAL_CROWN_SESSION_CAP_PERCENT,
        minVolume = minVolume,
        maxVolume = maxVolume
    )
    private var hideJob: Job? = null
    private var digitalCrownGuardState by mutableStateOf(DigitalCrownVolumeGuardState())
    private var playbackStartGuardDismissedByUser by mutableStateOf(false)
    private var virtualVolume by mutableFloatStateOf(readCurrentVolume().toFloat())

    var currentVolume by mutableIntStateOf(readCurrentVolume())
        private set

    var isVisible by mutableStateOf(false)
        private set

    val progress: Float
        get() {
            if (maxVolume <= minVolume) return 0f
            return ((virtualVolume - minVolume) / (maxVolume - minVolume)).coerceIn(0f, 1f)
        }

    val percentText: String
        get() = "${(progress * 100f).roundToInt()}%"

    fun adjustByDelta(delta: Float, eventUptimeMs: Long = SystemClock.elapsedRealtime()) {
        if (delta == 0f) return
        playbackStartGuardDismissedByUser = true
        syncOutOfBandVolumeChange()
        val guardedTarget = applyDigitalCrownVolumeGuard(
            currentVolume = virtualVolume,
            requestedDeltaVolume = delta * volumeSensitivity,
            minVolume = minVolume,
            maxVolume = maxVolume,
            guardEnabled = guardEnabled,
            sessionCapVolume = digitalCrownSessionCapVolume,
            previousState = digitalCrownGuardState,
            eventUptimeMs = eventUptimeMs
        )
        digitalCrownGuardState = guardedTarget.nextState
        virtualVolume = guardedTarget.targetVolume
        if (guardedTarget.shouldNotifyGuardTriggered) {
            onVolumeGuardTriggered()
        }
        setVolume(virtualVolume.roundToInt())
        show()
    }

    fun enforcePlaybackStartGuard() {
        syncOutOfBandVolumeChange()
        val current = readCurrentVolume()
        val targetVolume = playbackStartVolumeLimitPercent?.let {
            playbackStartVolumeForPercent(it, minVolume, maxVolume)
        }
        if (!shouldEnforcePlaybackStartGuard(
                playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
                dismissedByUser = playbackStartGuardDismissedByUser,
                currentVolume = current,
                minVolume = minVolume,
                maxVolume = maxVolume
            )
        ) {
            return
        }
        virtualVolume = targetVolume ?: current.toFloat()
        setVolume(virtualVolume.roundToInt())
        show()
    }

    private fun readCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    }

    private fun syncOutOfBandVolumeChange() {
        val actual = readCurrentVolume()
        if (actual == currentVolume) return
        playbackStartGuardDismissedByUser = true
        digitalCrownGuardState = DigitalCrownVolumeGuardState()
        currentVolume = actual
        virtualVolume = actual.toFloat()
    }

    private fun setVolume(target: Int) {
        val clampedTarget = target.coerceIn(minVolume, maxVolume)
        if (!audioManager.isVolumeFixed && clampedTarget != readCurrentVolume()) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clampedTarget, 0)
        }
        currentVolume = readCurrentVolume()
    }

    private fun show() {
        isVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(VOLUME_OVERLAY_HIDE_DELAY_MS)
            isVisible = false
        }
    }
}

@Composable
internal fun rememberPlayerVolumeState(
    guardEnabled: Boolean = true,
    playbackStartVolumeLimitPercent: Int? = PLAYBACK_START_VOLUME_LIMIT_PERCENT
): PlayerVolumeState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val audioManager = remember(appContext) {
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()
    return remember(audioManager, scope, guardEnabled, playbackStartVolumeLimitPercent, appContext) {
        PlayerVolumeState(
            audioManager = audioManager,
            scope = scope,
            guardEnabled = guardEnabled,
            playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
            onVolumeGuardTriggered = {
                Toast.makeText(appContext, VOLUME_GUARD_TRIGGERED_TOAST, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
internal fun PlayerVolumeOverlay(
    state: PlayerVolumeState,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.86f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "音量",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "：${state.percentText}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class DigitalCrownVolumeGuardState(
    val sessionCapVolume: Float? = null,
    val lastEventUptimeMs: Long = Long.MIN_VALUE,
    val guardNotificationShown: Boolean = false
)

private data class DigitalCrownVolumeGuardResult(
    val targetVolume: Float,
    val nextState: DigitalCrownVolumeGuardState,
    val shouldNotifyGuardTriggered: Boolean = false
)

private fun applyDigitalCrownVolumeGuard(
    currentVolume: Float,
    requestedDeltaVolume: Float,
    minVolume: Int,
    maxVolume: Int,
    guardEnabled: Boolean,
    sessionCapVolume: Float,
    previousState: DigitalCrownVolumeGuardState,
    eventUptimeMs: Long
): DigitalCrownVolumeGuardResult {
    val clampedCurrent = currentVolume.coerceIn(minVolume.toFloat(), maxVolume.toFloat())
    if (requestedDeltaVolume == 0f) {
        return DigitalCrownVolumeGuardResult(clampedCurrent, previousState)
    }

    val isNewSession = previousState.lastEventUptimeMs == Long.MIN_VALUE ||
        eventUptimeMs - previousState.lastEventUptimeMs > DIGITAL_CROWN_SESSION_IDLE_TIMEOUT_MS
    val direction = requestedDeltaVolume.compareTo(0f)
    var activeCapVolume = if (isNewSession || direction <= 0 || !guardEnabled) {
        null
    } else {
        previousState.sessionCapVolume
    }
    var guardNotificationShown = activeCapVolume?.let { previousState.guardNotificationShown } ?: false
    var targetVolume = (clampedCurrent + requestedDeltaVolume)
        .coerceIn(minVolume.toFloat(), maxVolume.toFloat())
    var wasLimitedByGuard = false

    if (guardEnabled && direction > 0) {
        val capActivationVolume = sessionCapVolume.roundToInt()
            .coerceIn(minVolume, maxVolume)
            .toFloat()
        val effectiveCapVolume = activeCapVolume ?: if (clampedCurrent < capActivationVolume) {
            sessionCapVolume
        } else {
            null
        }
        if (effectiveCapVolume != null) {
            activeCapVolume = effectiveCapVolume
            val cappedTargetVolume = targetVolume.coerceAtMost(effectiveCapVolume)
            wasLimitedByGuard = cappedTargetVolume < targetVolume
            targetVolume = cappedTargetVolume
        }
    }

    val shouldNotifyGuardTriggered = wasLimitedByGuard && !guardNotificationShown
    if (shouldNotifyGuardTriggered) guardNotificationShown = true
    return DigitalCrownVolumeGuardResult(
        targetVolume = targetVolume,
        nextState = DigitalCrownVolumeGuardState(
            sessionCapVolume = activeCapVolume,
            lastEventUptimeMs = eventUptimeMs,
            guardNotificationShown = guardNotificationShown
        ),
        shouldNotifyGuardTriggered = shouldNotifyGuardTriggered
    )
}

private fun shouldEnforcePlaybackStartGuard(
    playbackStartVolumeLimitPercent: Int?,
    dismissedByUser: Boolean,
    currentVolume: Int,
    minVolume: Int,
    maxVolume: Int
): Boolean {
    if (playbackStartVolumeLimitPercent == null || dismissedByUser) return false
    val targetVolume = playbackStartVolumeForPercent(
        playbackStartVolumeLimitPercent,
        minVolume,
        maxVolume
    )
    return currentVolume.coerceIn(minVolume, maxVolume).toFloat() > targetVolume
}

private fun playbackStartVolumeForPercent(
    targetPercent: Int,
    minVolume: Int,
    maxVolume: Int
): Float {
    return volumeForPercent(targetPercent.coerceIn(0, 100) / 100f, minVolume, maxVolume)
}

private fun volumeForPercent(targetPercent: Float, minVolume: Int, maxVolume: Int): Float {
    if (maxVolume <= minVolume || targetPercent <= 0f) return minVolume.toFloat()
    val range = (maxVolume - minVolume).coerceAtLeast(1)
    return (minVolume + range * targetPercent)
        .coerceIn((minVolume + 1).toFloat(), maxVolume.toFloat())
}

internal const val ROTARY_VOLUME_INPUT_SCALE = 0.01f

private const val PLAYBACK_START_VOLUME_LIMIT_PERCENT = 10
private const val VOLUME_OVERLAY_HIDE_DELAY_MS = 1_200L
private const val DIGITAL_CROWN_SESSION_IDLE_TIMEOUT_MS = 600L
private const val DIGITAL_CROWN_SESSION_CAP_PERCENT = 0.21f
private const val VOLUME_SENSITIVITY_PERCENT = 0.06f
private const val VOLUME_GUARD_TRIGGERED_TOAST = "音量调节防干扰触发，先停下以继续调整音量"
