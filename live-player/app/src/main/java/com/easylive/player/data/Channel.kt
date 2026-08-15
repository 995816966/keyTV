package com.easylive.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 频道实体。无论来源是 M3U 还是简易列表，进库后都是同一张平表，
 * 没有 group/分类字段——适老化：一个列表到底，不套分类。
 */
@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val logo: String? = null,
    val orderIndex: Int = 0
)
