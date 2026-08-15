package com.easylive.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置与状态存储（DataStore）。存放：
 * - 上次播放频道 id（用于开机/冷启动直接续播）
 * - 开机自启开关
 * - 当前直播源 URL
 * - 手动亮度（0~1，<=0 表示跟随系统）
 */
private val Context.dataStore by preferencesDataStore(name = "settings")

object Preferences {

    private val LAST_CHANNEL_ID = longPreferencesKey("last_channel_id")
    private val BOOT_START = booleanPreferencesKey("boot_start")
    private val SOURCE_URL = stringPreferencesKey("source_url")
    private val BRIGHTNESS = androidx.datastore.preferences.core.floatPreferencesKey("brightness")

    fun lastChannelId(context: Context): Flow<Long> =
        context.dataStore.data.map { it[LAST_CHANNEL_ID] ?: -1L }

    suspend fun setLastChannelId(context: Context, id: Long) {
        context.dataStore.edit { it[LAST_CHANNEL_ID] = id }
    }

    fun bootStart(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[BOOT_START] ?: false }

    suspend fun setBootStart(context: Context, on: Boolean) {
        context.dataStore.edit { it[BOOT_START] = on }
    }

    fun sourceUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[SOURCE_URL] ?: "" }

    suspend fun setSourceUrl(context: Context, url: String) {
        context.dataStore.edit { it[SOURCE_URL] = url }
    }

    fun brightness(context: Context): Flow<Float> =
        context.dataStore.data.map { it[BRIGHTNESS] ?: -1f }

    suspend fun setBrightness(context: Context, value: Float) {
        context.dataStore.edit { it[BRIGHTNESS] = value }
    }
}
