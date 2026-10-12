package com.luma.lifeos.domain.search

import com.luma.lifeos.domain.model.LifeEventRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class SearchQuery(
    val text: String = "",
    val from: LocalDate? = null,
    val through: LocalDate? = null,
    val tagIds: Set<UUID> = emptySet(),
    val personIds: Set<UUID> = emptySet(),
    val placeId: UUID? = null,
) {
    init {
        require(text.length <= 200)
        require(tagIds.size <= 100 && personIds.size <= 100)
        require(from == null || through == null || !from.isAfter(through))
    }
    fun bounds(zone: ZoneId) = SearchBounds(
        from?.atStartOfDay(zone)?.toInstant(),
        through?.takeUnless { it == LocalDate.MAX }?.plusDays(1)?.atStartOfDay(zone)?.toInstant(),
    )
}

data class SearchBounds(val start: Instant?, val endExclusive: Instant?)
data class SearchOption(val id: UUID, val name: String)
data class SearchOptions(
    val tags: List<SearchOption> = emptyList(),
    val people: List<SearchOption> = emptyList(),
    val places: List<SearchOption> = emptyList(),
)
data class SearchPage(val records: List<LifeEventRecord>, val offset: Int, val totalCount: Int) {
    init {
        require(offset >= 0 && totalCount >= 0)
        require(records.size.toLong() <= maxOf(0L, totalCount.toLong() - offset))
    }
}
