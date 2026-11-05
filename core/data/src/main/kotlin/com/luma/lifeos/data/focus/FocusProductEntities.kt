package com.luma.lifeos.data.focus

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Portable opt-in state; disabled is the safe default for pre-goal databases and archives. */
@Entity(tableName = "focus_goal_settings")
data class FocusGoalSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: Int = 1,
    @ColumnInfo(name = "enabled", defaultValue = "0") val enabled: Boolean = false,
    @ColumnInfo(name = "settings_revision", defaultValue = "0") val settingsRevision: Long = 0,
    @ColumnInfo(name = "visible_from_monday") val visibleFromMonday: String? = null,
    @ColumnInfo(name = "visible_from_zone_id") val visibleFromZoneId: String? = null,
)

@Entity(
    tableName = "focus_goal_version",
    foreignKeys = [
        ForeignKey(
            entity = FocusGoalSettingsEntity::class,
            parentColumns = ["singleton_id"],
            childColumns = ["settings_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["zone_id", "effective_monday"], unique = true),
        Index(value = ["settings_id"]),
    ],
)
data class FocusGoalVersionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "settings_id", defaultValue = "1") val settingsId: Int = 1,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "effective_monday") val effectiveMonday: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "target_minutes") val targetMinutes: Int,
    @ColumnInfo(name = "preferred_weekday_mask") val preferredWeekdayMask: Int,
)

/** Local-only command identity makes ambiguous goal writes safe to retry. Never backed up. */
@Entity(tableName = "focus_product_command_receipt", primaryKeys = ["database_generation", "command_id"])
data class FocusProductCommandReceiptEntity(
    @ColumnInfo(name = "database_generation") val databaseGeneration: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "result_type") val resultType: String,
)

/** Local catalog revision is generation-scoped and is deliberately excluded from portable backups. */
@Entity(tableName = "focus_tag_catalog_state")
data class FocusTagCatalogStateEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: Int = 1,
    @ColumnInfo(name = "revision", defaultValue = "0") val revision: Long = 0,
) {
    init { require(singletonId == 1 && revision >= 0) }
}

/** Stale tag labels and filters resolve to the current tag after a rename or explicit merge. */
@Entity(
    tableName = "focus_tag_alias",
    foreignKeys = [
        ForeignKey(
            entity = FocusTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["target_tag_id"])],
)
data class FocusTagAliasEntity(
    @PrimaryKey @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "former_tag_id") val formerTagId: String,
    @ColumnInfo(name = "target_tag_id") val targetTagId: String,
)
