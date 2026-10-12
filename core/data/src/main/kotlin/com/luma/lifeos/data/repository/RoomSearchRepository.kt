package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.data.search.SearchSql
import com.luma.lifeos.domain.repository.SearchRepository
import com.luma.lifeos.domain.search.*
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class RoomSearchRepository(private val database: LumaDatabase) : SearchRepository {
    override fun observeChanges() = RoomTimelineRepository(database).observeChanges()
    override fun observeOptions() = combine(database.catalog().observeTags(),
        database.catalog().observePeople(), database.catalog().observePlaces()) { tags, people, places ->
        SearchOptions(tags.map { SearchOption(UUID.fromString(it.id), it.name) },
            people.map { SearchOption(UUID.fromString(it.id), it.name) },
            places.map { SearchOption(UUID.fromString(it.id), it.name) })
    }
    override suspend fun page(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int): SearchPage {
        require(offset >= 0 && limit in 1..50)
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                val sql = SearchSql(query, zone)
                val dao = database.search()
                val count = dao.count(SimpleSQLiteQuery(sql.countSql, sql.arguments.toTypedArray()))
                val rows = dao.page(SimpleSQLiteQuery(sql.pageSql, (sql.arguments + listOf(limit, offset)).toTypedArray()))
                SearchPage(rows.map { it.toRecord() }, offset, count)
            }
        }
    }
}
