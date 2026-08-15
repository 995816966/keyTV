package com.easylive.player.player

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 透明手势层，盖在播放画面上。把屏幕宽度三等分：
 *  - 左 1/3 上下滑 → 调亮度
 *  - 中 + 右 2/3 上下滑 → 换台（上滑下一台，下滑上一台）
 *  - 轻点（无显著移动）→ 单击，呼出/收起控制层
 *
 * 上滑 = 增大（亮度增 / 下一台），下滑 = 减小（亮度减 / 上一台）。
 */
class GestureOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface Callback {
        fun onBrightnessDelta(delta: Float)   // delta: -1~1 的相对增量
        fun onChannelStep(direction: Int)      // direction: +1 下一台, -1 上一台
        fun onTap()
        fun isLocked(): Boolean
    }

    var callback: Callback? = null

    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var lastStepY = 0f
    private var leftZone = false
    private var tracking = false

    private val CHANNEL_STEP_PX = 90f   // 每滑动这么多像素换一台
    private val TAP_SLOP = 12f         // 移动小于此值算点击
    private val TAP_TIME = 250L        // 按下到抬起的时长阈值

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (callback?.isLocked() == true) return false  // 锁定后不响应任何手势

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startTime = System.currentTimeMillis()
                lastStepY = event.y
                leftZone = event.x < width / 3f
                tracking = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dy = event.y - startY
                val dx = event.x - startX
                // 仅处理明显纵向滑动
                if (abs(dy) > abs(dx) && abs(dy) > TAP_SLOP) {
                    if (leftZone) {
                        // 亮度：上滑(dy<0)→增亮
                        val delta = -dy / height.toFloat()
                        callback?.onBrightnessDelta(delta)
                        lastStepY = event.y
                        startY = event.y // 持续拖动用增量
                    } else {
                        // 换台：累计位移跨过台阶触发一次
                        val stepDy = event.y - lastStepY
                        if (abs(stepDy) >= CHANNEL_STEP_PX) {
                            if (stepDy < 0) callback?.onChannelStep(+1) else callback?.onChannelStep(-1)
                            lastStepY = event.y
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                tracking = false
                val dt = System.currentTimeMillis() - startTime
                val moved = abs(event.y - startY) + abs(event.x - startX)
                if (moved < TAP_SLOP && dt < TAP_TIME) {
                    callback?.onTap()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                return false
            }
        }
        return false
    }
}
