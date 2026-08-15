package com.easylive.player

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.easylive.player.data.AppDatabase
import com.easylive.player.data.Channel
import com.easylive.player.data.Preferences
import com.easylive.player.data.SourceRepository
import com.easylive.player.databinding.ActivityPlayerBinding
import com.easylive.player.player.BrightnessHelper
import com.easylive.player.player.GestureOverlay
import com.easylive.player.ui.ChannelListAdapter
import com.easylive.player.ui.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var adapter: ChannelListAdapter

    private var channels: List<Channel> = emptyList()
    private var current: Channel? = null
    private var locked = false
    private var controlsVisible = false

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private val brightHandler = Handler(Looper.getMainLooper())
    private val brightRunnable = Runnable { binding.brightnessHint.visibility = View.GONE }

    // 遥控：数字选台状态机 + 长按解锁
    private val numberHandler = Handler(Looper.getMainLooper())
    private val numberCommit = Runnable { commitNumber() }
    private val numberBuffer = StringBuilder()
    private var keyDownTime = 0L
    private val LONG_PRESS_MS = 500L

    // 遥控需拦截处理的按键集合（其余如 BACK/音量键交给系统）
    private val HANDLED_KEYS = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_GUIDE,
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupPlayer()
        setupGesture()
        setupControls()
        setupDrawer()
        ensureSource()
        observeChannels()
        handleBack()
    }

    /** 首次启动无直播源时，自动拉取默认源，确保开屏即有内容可播 */
    private fun ensureSource() {
        lifecycleScope.launch {
            val saved = Preferences.sourceUrl(this@PlayerActivity).first()
            if (saved.isNotEmpty()) return@launch
            val def = getString(R.string.default_source_url)
            try {
                val ch = SourceRepository.loadFromUrl(def)
                SourceRepository.refreshDb(this@PlayerActivity, ch)
                Preferences.setSourceUrl(this@PlayerActivity, def)
            } catch (_: Exception) {
                // 拉取失败则保持空状态，由用户手动添加
            }
        }
    }

    // ---------- 播放器 ----------
    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> binding.loading.visibility = View.VISIBLE
                        Player.STATE_READY -> binding.loading.visibility = View.GONE
                        Player.STATE_ENDED, Player.STATE_IDLE -> binding.loading.visibility = View.GONE
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    binding.loading.visibility = View.GONE
                    binding.errorTip.visibility = View.VISIBLE
                    binding.errorTip.text = "播放失败：${error.message}"
                }
            })
        }
        binding.playerView.player = player
        binding.playerView.useController = false
    }

    private fun playChannel(ch: Channel) {
        current = ch
        lifecycleScope.launch { Preferences.setLastChannelId(this@PlayerActivity, ch.id) }
        adapter.setCurrent(ch.id)
        binding.errorTip.visibility = View.GONE
        player.setMediaItem(MediaItem.fromUri(ch.url))
        player.prepare()
        player.playWhenReady = true
    }

    private fun stepChannel(dir: Int) {
        if (channels.isEmpty()) return
        val idx = channels.indexOfFirst { it.id == current?.id }.let { if (it < 0) 0 else it }
        val next = channels[(idx + dir + channels.size) % channels.size]
        playChannel(next)
    }

    // ---------- 手势 ----------
    private fun setupGesture() {
        binding.gestureOverlay.callback = object : GestureOverlay.Callback {
            override fun onBrightnessDelta(delta: Float) {
                val v = BrightnessHelper.applyDelta(this@PlayerActivity, delta)
                showBrightnessHint(v)
            }
            override fun onChannelStep(direction: Int) {
                stepChannel(direction)
            }
            override fun onTap() {
                if (locked) return
                if (controlsVisible) hideControls() else showControls()
            }
            override fun isLocked() = locked
        }
    }

    // ---------- 控制层 ----------
    private fun setupControls() {
        binding.btnSettings.setOnClickListener { SettingsActivity.start(this) }
        binding.btnChannels.setOnClickListener { openChannelList() }
        binding.btnListSettings.setOnClickListener {
            binding.drawer.closeDrawers()
            SettingsActivity.start(this)
        }
        binding.btnLock.setOnClickListener { toggleLock() }
        binding.lockBadge.setOnClickListener { toggleLock() } // 锁定态下点击解锁
        binding.emptyGoSettings.setOnClickListener { SettingsActivity.start(this) }
    }

    /** 打开频道列表并把焦点交给网格（遥控可用方向键导航） */
    private fun openChannelList() {
        binding.drawer.openDrawer(GravityCompat.END)
        binding.channelRecycler.post { binding.channelRecycler.requestFocus() }
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlBar.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun hideControls() {
        if (locked) return
        controlsVisible = false
        binding.controlBar.visibility = View.GONE
        // 注意：不在此关闭抽屉，列表打开后保持，由返回/菜单键关闭
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    private fun toggleLock() {
        locked = !locked
        if (locked) {
            binding.controlBar.visibility = View.GONE
            binding.lockBadge.visibility = View.VISIBLE
        } else {
            binding.lockBadge.visibility = View.GONE
            showControls()
        }
    }

    private fun showBrightnessHint(v: Float) {
        binding.brightnessHint.text = "亮度 ${(v * 100).toInt()}%"
        binding.brightnessHint.visibility = View.VISIBLE
        brightHandler.removeCallbacks(brightRunnable)
        brightHandler.postDelayed(brightRunnable, 1000)
    }

    // ---------- 频道列表（全屏网格） ----------
    private fun setupDrawer() {
        adapter = ChannelListAdapter { ch ->
            playChannel(ch)
            binding.drawer.closeDrawers()
        }
        binding.channelRecycler.layoutManager = GridLayoutManager(this, computeSpan())
        binding.channelRecycler.adapter = adapter
    }

    /** 自适应列数：保证每列足够宽让名称显示全，至少两列 */
    private fun computeSpan(): Int {
        val dm = resources.displayMetrics
        val minCell = (300f * dm.density).toInt().coerceAtLeast(1)
        return maxOf(2, dm.widthPixels / minCell)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转后按新宽度重算列数（configChanges 已拦旋转，不重建 Activity）
        (binding.channelRecycler.layoutManager as? GridLayoutManager)?.spanCount = computeSpan()
    }

    // ---------- 数据观察 ----------
    private fun observeChannels() {
        lifecycleScope.launch {
            AppDatabase.get(this@PlayerActivity).channelDao().observeAll().collect { list ->
                channels = list
                adapter.submitList(list)
                if (list.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.controlBar.visibility = View.GONE
                    current = null
                    return@collect
                }
                binding.emptyState.visibility = View.GONE
                // 仅首次决定起播频道：上次播放的，没有就第一个
                if (current == null) {
                    val lastId = Preferences.lastChannelId(this@PlayerActivity).first()
                    val start = list.firstOrNull { it.id == lastId } ?: list.first()
                    playChannel(start)
                }
            }
        }
    }

    // ---------- 遥控按键（KeyEvent，与触摸手势并列） ----------
    // 列表关闭：上/下=换台(循环)，左/右=亮度，OK=出控制层，菜单=开列表，数字=选台
    // 列表打开：方向键在网格内导航焦点，OK=选中当前项，菜单/返回=关列表
    // 锁定只封触摸；遥控在锁定时仍可用，长按任意键解锁
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val action = event.action
        val drawerOpen = binding.drawer.isDrawerOpen(GravityCompat.END)

        // 数字键：累积选台（锁定时也可；列表打开时也累积，确认后跳播）
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                appendDigit(code - KeyEvent.KEYCODE_0)
            }
            return true
        }

        // 长按解锁检测（基于按下/抬起时长）
        if (action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) keyDownTime = System.currentTimeMillis()
        } else {
            val held = System.currentTimeMillis() - keyDownTime
            if (locked && held >= LONG_PRESS_MS) {
                toggleLock()
                return true
            }
        }

        // 列表打开时：方向键交给系统做网格焦点导航；OK 选中；菜单/返回关列表
        if (drawerOpen) {
            when (code) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_GUIDE -> {
                    binding.drawer.closeDrawers()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (numberBuffer.isNotEmpty()) {
                        commitNumber()
                        return true
                    }
                    return super.dispatchKeyEvent(event) // 触发当前焦点 item 点击
                }
                else -> return super.dispatchKeyEvent(event) // 方向键焦点导航（含 BACK 关列表）
            }
        }

        // 列表关闭：仅拦截遥控常用键；BACK、音量键等放行
        if (code !in HANDLED_KEYS) return super.dispatchKeyEvent(event)

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) keyDownTime = System.currentTimeMillis()
            return true
        }

        // ACTION_UP：短按功能
        if (locked) {
            // 锁定仅允许观看操作，不开控制层/抽屉（防误触）
            when (code) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> stepChannel(+1)
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> stepChannel(-1)
                KeyEvent.KEYCODE_DPAD_LEFT -> adjustBrightness(-0.05f)
                KeyEvent.KEYCODE_DPAD_RIGHT -> adjustBrightness(+0.05f)
            }
            return true
        }

        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> stepChannel(+1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> stepChannel(-1)
            KeyEvent.KEYCODE_DPAD_LEFT -> adjustBrightness(-0.05f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> adjustBrightness(+0.05f)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (numberBuffer.isNotEmpty()) commitNumber() else toggleControlsByKey()
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_GUIDE -> openDrawerByKey()
        }
        return true
    }

    private fun adjustBrightness(delta: Float) {
        val v = BrightnessHelper.applyDelta(this, delta)
        showBrightnessHint(v)
    }

    private fun toggleControlsByKey() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun openDrawerByKey() {
        openChannelList()
        showControls()
    }

    private fun appendDigit(d: Int) {
        numberBuffer.append(d)
        if (numberBuffer.length > 4) numberBuffer.deleteCharAt(0) // 最多 4 位
        binding.numberOverlay.text = numberBuffer.toString()
        binding.numberOverlay.visibility = View.VISIBLE
        numberHandler.removeCallbacks(numberCommit)
        numberHandler.postDelayed(numberCommit, 1500) // 超时自动确认
    }

    private fun commitNumber() {
        val n = numberBuffer.toString().toIntOrNull()
        numberBuffer.clear()
        numberHandler.removeCallbacks(numberCommit)
        binding.numberOverlay.visibility = View.GONE
        if (n != null && n in 1..channels.size) {
            playChannel(channels[n - 1]) // 数字 = 列表序号（第 N 个频道）
            if (binding.drawer.isDrawerOpen(GravityCompat.END)) binding.drawer.closeDrawers()
        }
    }

    // ---------- 返回键：先关抽屉/控制层 ----------
    private fun handleBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) return@handleOnBackPressed // 锁定下忽略返回，防误退
                when {
                    binding.drawer.isDrawerOpen(GravityCompat.END) -> binding.drawer.closeDrawers()
                    controlsVisible -> hideControls()
                    else -> finish()
                }
            }
        })
    }

    // ---------- 生命周期 ----------
    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        player.play() // 锁定只封触摸/控制层，不影响播放，回前台继续播
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        brightHandler.removeCallbacks(brightRunnable)
        numberHandler.removeCallbacks(numberCommit)
        player.release()
    }
}
