package com.luma.lifeos.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "usage_session", primaryKeys = ["package_name", "started_at"],
    indices = [Index(value = ["started_at"]), Index(value = ["ended_at"])])
data class UsageSessionEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "app_label") val appLabel: String?,
    val ongoing: Boolean,
)

@Entity(tableName = "usage_session_window", primaryKeys = ["query_start", "query_end"])
data class UsageSessionWindowEntity(
    @ColumnInfo(name = "query_start") val queryStart: Long,
    @ColumnInfo(name = "query_end") val queryEnd: Long,
    val complete: Boolean,
)
