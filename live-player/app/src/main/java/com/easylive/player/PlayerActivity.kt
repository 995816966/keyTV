package com.easylive.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
        binding.btnChannels.setOnClickListener { binding.drawer.openDrawer(GravityCompat.END) }
        binding.btnLock.setOnClickListener { toggleLock() }
        binding.lockBadge.setOnClickListener { toggleLock() } // 锁定态下点击解锁
        binding.emptyGoSettings.setOnClickListener { SettingsActivity.start(this) }
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
        binding.drawer.closeDrawers()
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

    // ---------- 抽屉频道列表 ----------
    private fun setupDrawer() {
        adapter = ChannelListAdapter { ch ->
            playChannel(ch)
            binding.drawer.closeDrawers()
        }
        binding.channelRecycler.layoutManager = LinearLayoutManager(this)
        binding.channelRecycler.adapter = adapter
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

    // ---------- 返回键：先关抽屉/控制层 ----------
    private fun handleBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
        if (!locked) player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        brightHandler.removeCallbacks(brightRunnable)
        player.release()
    }
}
