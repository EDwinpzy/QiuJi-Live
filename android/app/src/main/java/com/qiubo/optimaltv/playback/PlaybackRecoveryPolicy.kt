package com.qiubo.optimaltv.playback

enum class RecoveryAction {
    REFRESH_CURRENT,
    SWITCH_SOURCE,
    RECREATE_ENGINE,
    RESET_RUNTIME,
    GIVE_UP,
}

/** Pure recovery ladder shared by TV/mobile player coordinators. */
object PlaybackRecoveryPolicy {
    const val STALL_THRESHOLD_MS = 8_000L
    fun isStalled(durationMs: Long): Boolean = durationMs >= STALL_THRESHOLD_MS

    fun action(attempt: Int, hasAlternate: Boolean = false): RecoveryAction = when {
        attempt > 0 && hasAlternate -> RecoveryAction.SWITCH_SOURCE
        else -> actionWithoutAlternate(attempt)
    }

    private fun actionWithoutAlternate(attempt: Int): RecoveryAction = when (attempt) {
        0 -> RecoveryAction.REFRESH_CURRENT
        1 -> RecoveryAction.SWITCH_SOURCE
        2 -> RecoveryAction.RECREATE_ENGINE
        3 -> RecoveryAction.RESET_RUNTIME
        else -> RecoveryAction.GIVE_UP
    }

    /** A live source gets one fresh same-source retry before we move on. */
    fun liveLineFailureAction(retryCount: Int, hasAlternate: Boolean): RecoveryAction = when {
        retryCount <= 0 -> RecoveryAction.REFRESH_CURRENT
        hasAlternate -> RecoveryAction.SWITCH_SOURCE
        else -> RecoveryAction.GIVE_UP
    }

    class Generation {
        private var current = 0L

        fun next(): Long = ++current
        fun accepts(value: Long): Boolean = value == current
    }
}

/** Live HLS offset policy shared by both Android clients. */
object LiveWindowPolicy {
    const val SMALL_WINDOW_MAX_MS = 12_000L
    const val MAX_OFFSET_MS = 5_000L
    private const val TARGET_OFFSET_MS = 4_000L

    data class BufferDurations(
        val minMs: Int,
        val maxMs: Int,
        val playMs: Int,
        val rebufferMs: Int,
    )

    fun isSmall(windowMs: Long): Boolean = windowMs in 1..SMALL_WINDOW_MAX_MS

    /** Keep the product target near the live edge while never exceeding the 5s budget. */
    @Suppress("UNUSED_PARAMETER")
    fun targetOffsetMs(windowMs: Long, segmentMs: Long = 0L): Long =
        if (isSmall(windowMs)) (windowMs * 3 / 5).coerceIn(1_200L, TARGET_OFFSET_MS)
        else if (windowMs > 0L) TARGET_OFFSET_MS.coerceAtMost(windowMs - 500L).coerceAtLeast(800L)
        else 3_000L

    fun minOffsetMs(windowMs: Long, segmentMs: Long = 0L): Long =
        (targetOffsetMs(windowMs, segmentMs) - 1_500L).coerceAtLeast(500L)

    fun maxOffsetMs(windowMs: Long, segmentMs: Long = 0L): Long =
        (targetOffsetMs(windowMs, segmentMs) + 1_000L).coerceAtMost(MAX_OFFSET_MS).let {
            if (windowMs > 0L) it.coerceAtMost(windowMs - 500L).coerceAtLeast(1_000L) else it
        }

    /**
     * LoadControl profile that stays reachable inside a short rolling HLS window.
     *
     * A 6s window cannot ever satisfy the old 8s minimum. Keep the minimum below the
     * playback offset and cap the maximum at the safe part of the actual window.
     */
    fun bufferDurations(windowMs: Long, segmentMs: Long = 0L): BufferDurations {
        if (isSmall(windowMs)) {
            val safeWindow = (windowMs - 500L).coerceAtLeast(1_500L)
            val target = targetOffsetMs(windowMs, segmentMs)
            val segment = segmentMs.takeIf { it > 0L } ?: 2_000L
            val minBuf = (target - 500L).coerceIn(500L, safeWindow)
            val maxBuf = (target + segment).coerceAtLeast(minBuf).coerceAtMost(safeWindow)
            return BufferDurations(
                minMs = minBuf.toInt(),
                maxMs = maxBuf.toInt(),
                playMs = minOf(target, 1_500L).coerceAtLeast(500L).coerceAtMost(minBuf).toInt(),
                rebufferMs = minOf(1_500L, minBuf).coerceAtLeast(500L).toInt(),
            )
        }
        if (windowMs in 13_000L..60_000L) {
            val maxBuf = (windowMs - 500L).coerceIn(8_000L, 60_000L)
            val target = targetOffsetMs(windowMs, segmentMs)
            val segment = segmentMs.takeIf { it > 0L } ?: 4_000L
            val minBuf = (target + segment).coerceIn(6_000L, maxBuf)
            return BufferDurations(
                minMs = minBuf.toInt(),
                maxMs = maxBuf.toInt(),
                playMs = target.coerceIn(1_000L, 2_500L).toInt(),
                rebufferMs = 3_000,
            )
        }
        return BufferDurations(minMs = 30_000, maxMs = 120_000, playMs = 1_000, rebufferMs = 2_500)
    }
}

