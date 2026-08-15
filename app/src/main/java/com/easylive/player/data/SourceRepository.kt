package com.easylive.player.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * 直播源解析与加载。
 * 支持两种格式，解析后都转成扁平的 Channel 列表（忽略 M3U 的 group-title 分类）：
 *  1) M3U / M3U8：#EXTINF 行取名称与 tvg-logo，下一行取地址
 *  2) 简易列表：一行一个，`名称,地址` 或 `名称,地址,台标`（逗号分隔，UTF-8）
 */
object SourceRepository {

    private const val TAG = "SourceRepository"

    /** 从网络 URL 拉取并解析（IO 线程） */
    suspend fun loadFromUrl(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val text = URL(url).readText(charset = Charsets.UTF_8)
        parse(text)
    }

    /** 从本地文本内容解析 */
    fun parse(text: String): List<Channel> {
        val trimmed = text.trimStart('\uFEFF') // 去 BOM
        return if (trimmed.startsWith("#EXTM3U")) parseM3u(trimmed)
        else parseSimple(trimmed)
    }

    private fun parseM3u(text: String): List<Channel> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                // #EXTINF:-1 tvg-logo="x.png" tvg-id=".." group-title="..",频道名
                val comma = line.indexOf(',')
                val name = if (comma >= 0) line.substring(comma + 1).trim() else line
                val logo = "tvg-logo=\"([^\"]*)\"".toRegex().find(line)?.groupValues?.get(1)?.ifEmpty { null }
                pendingName = name.ifBlank { null }
                pendingLogo = logo
            } else if (line.startsWith("#")) {
                // 其他注释行（含 #EXTGRP 等）忽略
                continue
            } else {
                // 地址行
                val url = line
                result.add(
                    Channel(
                        name = pendingName ?: url,
                        url = url,
                        logo = pendingLogo,
                        orderIndex = result.size
                    )
                )
                pendingName = null
                pendingLogo = null
            }
        }
        return result
    }

    private fun parseSimple(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach // 空行/注释
            val parts = line.split(",")
            when (parts.size) {
                1 -> result.add(Channel(name = parts[0], url = parts[0], orderIndex = result.size))
                else -> {
                    val name = parts[0].trim()
                    val url = parts[1].trim()
                    val logo = if (parts.size >= 3) parts[2].trim().ifEmpty { null } else null
                    result.add(Channel(name = name.ifEmpty { url }, url = url, logo = logo, orderIndex = result.size))
                }
            }
        }
        return result
    }

    /** 解析后写入数据库（先清空再插入，保证来源更新干净） */
    suspend fun refreshDb(context: Context, channels: List<Channel>) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).channelDao()
        dao.clear()
        if (channels.isNotEmpty()) dao.insertAll(channels)
        Log.i(TAG, "刷新频道完成，共 ${channels.size} 个")
    }
}
