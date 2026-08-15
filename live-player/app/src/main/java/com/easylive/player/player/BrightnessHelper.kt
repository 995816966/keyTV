package com.easylive.player.player

import android.app.Activity
import android.view.WindowManager

/**
 * 屏幕亮度控制。范围 0.05~1.0，避免全黑。
 * 启动时读取系统当前亮度作为基准。
 */
object BrightnessHelper {

    private const val MIN = 0.05f

    fun getCurrent(activity: Activity): Float {
        val b = activity.window.attributes.screenBrightness
        return if (b < 0) 0.5f else b
    }

    /** 在基准上叠加增量，并写回窗口 */
    fun applyDelta(activity: Activity, delta: Float): Float {
        val cur = getCurrent(activity)
        val next = (cur + delta).coerceIn(MIN, 1f)
        set(activity, next)
        return next
    }

    fun set(activity: Activity, value: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = value.coerceIn(MIN, 1f)
        activity.window.attributes = lp
    }
}
