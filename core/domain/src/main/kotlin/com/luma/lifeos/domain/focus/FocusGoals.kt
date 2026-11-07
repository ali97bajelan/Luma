package com.luma.lifeos.domain.focus

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

const val FOCUS_GOAL_MIN_MINUTES = 1
const val FOCUS_GOAL_MAX_MINUTES = 10_080

/** A goal version starts on an ISO Monday in its captured timezone and recurs weekly. */
data class FocusGoalVersion(
    val id: UUID,
    val revision: Long,
    val effectiveMonday: LocalDate,
    val zoneId: String,
    val targetMinutes: Int,
    val preferredDays: Set<DayOfWeek>,
) {
    init {
        require(revision > 0) { "Goal version revision must be positive" }
        require(effectiveMonday.dayOfWeek == DayOfWeek.MONDAY) { "Goal versions start on Monday" }
        require(ZoneId.of(zoneId).id == zoneId) { "Goal timezone must be canonical" }
        require(targetMinutes in FOCUS_GOAL_MIN_MINUTES..FOCUS_GOAL_MAX_MINUTES)
        require(preferredDays.isNotEmpty()) { "Choose at least one preferred focus day" }
        require(preferredDays.size <= 7)
    }

    val weekdayMask: Int get() = preferredDays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
}

data class FocusGoalState(
    val enabled: Boolean,
    val settingsRevision: Long,
    val visibleFromMonday: LocalDate?,
    val visibleFromZoneId: String?,
    val versions: List<FocusGoalVersion>,
) {
    init {
        require(settingsRevision >= 0)
        require((visibleFromMonday == null) == (visibleFromZoneId == null))
        require(visibleFromMonday == null || visibleFromMonday.dayOfWeek == DayOfWeek.MONDAY)
        visibleFromZoneId?.let { require(ZoneId.of(it).id == it) }
        require(!enabled || visibleFromMonday != null) { "Enabled goals require a visible-from boundary" }
        require(versions.map(FocusGoalVersion::id).distinct().size == versions.size)
        require(versions.map { it.zoneId to it.effectiveMonday }.distinct().size == versions.size)
    }

    /** Latest activated version after the current enable boundary; prior versions stay historical. */
    fun activeGoal(now: Instant): FocusGoalVersion? {
        if (!enabled) return null
        val visibleMonday = visibleFromMonday ?: return null
        val visibleZone = ZoneId.of(requireNotNull(visibleFromZoneId))
        val visibleInstant = visibleMonday.atStartOfDay(visibleZone).toInstant()
        if (now.isBefore(visibleInstant)) return null
        return versions.asSequence()
            .map { version -> version to version.effectiveMonday.atStartOfDay(ZoneId.of(version.zoneId)).toInstant() }
            .filter { (_, activation) -> !activation.isBefore(visibleInstant) && !activation.isAfter(now) }
            .maxWithOrNull(compareBy<Pair<FocusGoalVersion, Instant>>({ it.second }, { it.first.id.toString() }))
            ?.first
    }

    fun pendingGoal(now: Instant): FocusGoalVersion? = versions.asSequence()
        .map { version -> version to version.effectiveMonday.atStartOfDay(ZoneId.of(version.zoneId)).toInstant() }
        .filter { (_, activation) -> activation.isAfter(now) }
        .minWithOrNull(compareBy<Pair<FocusGoalVersion, Instant>>({ it.second }, { it.first.id.toString() }))
        ?.first

    companion object {
        val DISABLED = FocusGoalState(
            enabled = false,
            settingsRevision = 0,
            visibleFromMonday = null,
            visibleFromZoneId = null,
            versions = emptyList(),
        )
    }
}

data class FocusWeekDistribution(val activeDays: Int, val highestDate: LocalDate?) {
    init {
        require(activeDays in 0..7)
        require((activeDays == 0) == (highestDate == null))
    }
}

/** ISO Monday strictly after the local date, including when called on a Monday. */
fun nextGoalMonday(now: Instant, zone: ZoneId): LocalDate =
    now.atZone(zone).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))

/** Positive saved contributions count; ties select the earliest local date. */
fun focusWeekDistribution(dailyTotals: Map<LocalDate, Long>): FocusWeekDistribution {
    require(dailyTotals.size <= 7) { "A focus week has at most seven daily totals" }
    val monday = dailyTotals.keys.firstOrNull()?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    require(dailyTotals.keys.all { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) == monday }) {
        "Daily focus totals must belong to one ISO week"
    }
    require(dailyTotals.values.all { it >= 0 }) { "Daily focus totals cannot be negative" }
    val active = dailyTotals.filterValues { it > 0 }
    val highestDate = active.entries.minWithOrNull(
        compareBy<Map.Entry<LocalDate, Long>>({ -it.value }, { it.key }),
    )?.key
    return FocusWeekDistribution(activeDays = active.size, highestDate = highestDate)
}

data class FocusProductContext(val commandId: UUID, val generation: UUID)

data class FocusTagCatalog(
    val revision: Long,
    val tags: List<FocusTag>,
    /** Former tag IDs mapped to their current destination after an explicit merge. */
    val mergedIds: Map<UUID, UUID> = emptyMap(),
    val databaseGeneration: UUID? = null,
) {
    init { require(revision >= 0) }

    fun resolve(id: UUID): FocusTag? {
        var current = id
        repeat(mergedIds.size + 1) {
            val next = mergedIds[current] ?: return tags.firstOrNull { it.id == current }
            if (next == current) return tags.firstOrNull { it.id == current }
            current = next
        }
        return null
    }
}

enum class FocusProductInvalidReason {
    INVALID_GOAL,
    INVALID_INVITATION,
    INVALID_REFLECTION,
    INVALID_SESSION_EDIT,
    INVALID_TAG,
    INVALID_COMMAND,
}

data class FocusProductUnavailableReason(val code: String = "storage")

sealed interface FocusProductResult {
    data object Applied : FocusProductResult
    data object AlreadyApplied : FocusProductResult
    data class Conflict(val current: FocusGoalState) : FocusProductResult
    data class InvitationConflict(val current: FocusInvitationConfig) : FocusProductResult
    data class SessionConflict(val actualRevision: Long?) : FocusProductResult
    data class TagCatalogConflict(val current: FocusTagCatalog) : FocusProductResult
    data class MergeRequired(val source: FocusTag, val target: FocusTag) : FocusProductResult
    data class Invalid(val reason: FocusProductInvalidReason) : FocusProductResult
    data class Unavailable(val reason: FocusProductUnavailableReason = FocusProductUnavailableReason()) : FocusProductResult
}

/** Versioned, generation-aware local persistence for optional focus goals. */
interface FocusProductRepository {
    suspend fun currentGeneration(): UUID
    fun observeGoals(): Flow<FocusGoalState>
    suspend fun saveReflection(
        sessionId: UUID,
        expectedRevision: Long,
        reflection: FocusReflection,
        context: FocusProductContext,
    ): FocusProductResult
    fun observeTagCatalog(): Flow<FocusTagCatalog>
    /** Sum of credited runs from linked terminal sessions; open time is never included. */
    fun observeTaskFocusMillis(taskId: UUID): Flow<Long>
    suspend fun updateSessionMetadata(
        sessionId: UUID,
        expectedRevision: Long,
        name: String?,
        tagName: String?,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult
    suspend fun renameTag(
        tagId: UUID,
        name: String,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult
    suspend fun mergeTags(
        sourceTagId: UUID,
        targetTagId: UUID,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult
    suspend fun scheduleGoal(
        goal: FocusGoalVersion,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult
    suspend fun setGoalsEnabled(
        enabled: Boolean,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult
}
