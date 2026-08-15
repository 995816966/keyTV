package com.easylive.player.player

import android.graphics.BitmapFactory
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * 极简台标加载器，不引入第三方图片库。
 * 有 logo 地址就异步下载并解码；失败或为空则保持调用方设置的占位（首字圆形）。
 */
object LogoLoader {

    fun load(iv: ImageView, url: String?) {
        if (url.isNullOrEmpty()) return
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection().apply {
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    val stream = conn.getInputStream()
                    BitmapFactory.decodeStream(stream)
                }
                iv.setImageBitmap(bmp)
            } catch (_: Exception) {
                // 静默失败，保留占位
            }
        }
    }
}
