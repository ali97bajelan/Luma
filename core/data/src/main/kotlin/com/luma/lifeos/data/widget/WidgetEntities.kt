package com.luma.lifeos.data.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "widget_instance")
data class WidgetInstanceEntity(
    @PrimaryKey @ColumnInfo(name = "widget_id") val widgetId: Int,
    val kind: String,
    // Legacy column retained for database compatibility; its value no longer controls widget content.
    @ColumnInfo(name = "show_details") val showDetails: Boolean,
    @ColumnInfo(name = "feedback_json") val feedbackJson: String?,
)

@Entity(
    tableName = "widget_command",
    foreignKeys = [ForeignKey(
        entity = WidgetInstanceEntity::class,
        parentColumns = ["widget_id"],
        childColumns = ["widget_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["widget_id", "status", "fingerprint"]),
        Index(value = ["created_at"]),
    ],
)
data class WidgetCommandEntity(
    @PrimaryKey val token: String,
    @ColumnInfo(name = "widget_id") val widgetId: Int,
    val action: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val fingerprint: String,
    val status: String,
    @ColumnInfo(name = "outcome_json") val outcomeJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
