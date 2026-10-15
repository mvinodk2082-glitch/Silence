package com.silence.blocker.media

/**
 * Pure state machine for mute/unmute decisions (unit-testable, no Android).
 * - Mute only while an ad is PLAYING.
 * - Unmute on content, pause/stop, session gone, or after [maxMuteMs] (watchdog).
 * - After a watchdog or user "unmute now", the same item is not muted again.
 */
class MuteDecider(private val maxMuteMs: Long = MAX_MUTE_MS) {
    enum class Action { MUTE, UNMUTE, NONE }

    var muted = false; private set
    private var mutedAt = 0L
    private var mutedKey: String? = null
    private var suppressedKey: String? = null
    var newAdCount = 0; private set
    private var lastCountedKey: String? = null

    /** @param adKey non-null when the current item is an ad that is playing. */
    fun onEvaluate(adKey: String?, now: Long): Action {
        if (adKey != null && adKey != suppressedKey) {
            if (!muted) {
                muted = true; mutedAt = now; mutedKey = adKey
                if (adKey != lastCountedKey) { lastCountedKey = adKey; newAdCount++ }
                return Action.MUTE
            }
            if (adKey != mutedKey) { mutedKey = adKey; mutedAt = now
                if (adKey != lastCountedKey) { lastCountedKey = adKey; newAdCount++ } }
            return if (now - mutedAt >= maxMuteMs) watchdog() else Action.NONE
        }
        if (adKey == null) { suppressedKey = null; lastCountedKey = null }
        return if (muted) release() else Action.NONE
    }

    fun onTick(now: Long): Action = if (muted && now - mutedAt >= maxMuteMs) watchdog() else Action.NONE

    fun userUnmute(): Action { suppressedKey = mutedKey; return if (muted) release() else Action.NONE }

    private fun watchdog(): Action { suppressedKey = mutedKey; return release() }
    private fun release(): Action { muted = false; mutedKey = null; return Action.UNMUTE }

    companion object { const val MAX_MUTE_MS = 35_000L }
}
