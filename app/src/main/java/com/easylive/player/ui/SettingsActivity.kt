package com.easylive.player.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.easylive.player.data.AppDatabase
import com.easylive.player.data.Preferences
import com.easylive.player.data.SourceRepository
import com.easylive.player.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页：开机启动 + 直播源管理（URL 拉取 / 本地文件导入 / 清空）。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        loadFromFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 开机自启开关
        lifecycleScope.launch {
            val on = Preferences.bootStart(this@SettingsActivity).first()
            binding.bootSwitch.isChecked = on
        }
        binding.bootSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { Preferences.setBootStart(this@SettingsActivity, isChecked) }
        }

        // 当前源 URL
        lifecycleScope.launch {
            binding.urlInput.setText(Preferences.sourceUrl(this@SettingsActivity).first())
            refreshCount()
        }

        binding.btnUpdate.setOnClickListener { updateFromUrl() }
        binding.btnImport.setOnClickListener { pickFile.launch(arrayOf("*/*")) }
        binding.btnClear.setOnClickListener { clearAll() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun updateFromUrl() {
        val url = binding.urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "请先填写直播源地址", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnUpdate.isEnabled = false
        lifecycleScope.launch {
            try {
                val channels = SourceRepository.loadFromUrl(url)
                SourceRepository.refreshDb(this@SettingsActivity, channels)
                Preferences.setSourceUrl(this@SettingsActivity, url)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "已更新 ${channels.size} 个频道", Toast.LENGTH_LONG).show()
                    refreshCount()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "拉取失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { binding.btnUpdate.isEnabled = true }
            }
        }
    }

    private fun loadFromFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("无法读取文件")
                val channels = SourceRepository.parse(text)
                SourceRepository.refreshDb(this@SettingsActivity, channels)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "已导入 ${channels.size} 个频道", Toast.LENGTH_LONG).show()
                    refreshCount()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.get(this@SettingsActivity).channelDao().clear()
            Preferences.setLastChannelId(this@SettingsActivity, -1L)
            withContext(Dispatchers.Main) { refreshCount() }
        }
    }

    private suspend fun refreshCount() {
        val n = AppDatabase.get(this@SettingsActivity).channelDao().count()
        withContext(Dispatchers.Main) {
            binding.countText.text = "当前频道数：$n"
        }
    }

    companion object {
        fun start(activity: Activity) = activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }
}
