package com.qiubo.optimaltv.lifecycle

/** 首页两次返回的判定窗口；与提示显示时长保持一致。 */
const val DOUBLE_BACK_EXIT_WINDOW_MS = 2_500L

enum class DoubleBackDecision {
    PROMPT,
    EXIT,
}

/**
 * 首页双击返回判定。
 *
 * 调用方只在首页路由下调用；离开首页时必须 [reset]，避免播放器返回后误用旧时间窗。
 */
class DoubleBackExitPolicy(
    private val windowMs: Long = DOUBLE_BACK_EXIT_WINDOW_MS,
) {
    private var lastBackAtMs: Long? = null

    fun onBack(nowMs: Long): DoubleBackDecision {
        val last = lastBackAtMs
        if (last != null && nowMs >= last && nowMs - last <= windowMs) {
            lastBackAtMs = null
            return DoubleBackDecision.EXIT
        }
        lastBackAtMs = nowMs
        return DoubleBackDecision.PROMPT
    }

    fun reset() {
        lastBackAtMs = null
    }
}
