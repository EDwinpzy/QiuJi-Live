package com.qiubo.optimaltv.lifecycle

/**
 * 首页是否处于返回键的最终退出点（Hero 已聚焦且列表已回到顶部）。
 *
 * LiveScreen 负责上报，MainActivity 只在退出点启用双击返回。
 */
object HomeBackState {
    @Volatile
    var isAtExitPoint: Boolean = false
        private set

    /** 每次状态变化递增，防止用户离开退出点后又回到原处时复用旧时间窗。 */
    @Volatile
    var version: Long = 0L
        private set

    fun update(atExitPoint: Boolean) {
        if (isAtExitPoint == atExitPoint) return
        isAtExitPoint = atExitPoint
        version++
    }
}
