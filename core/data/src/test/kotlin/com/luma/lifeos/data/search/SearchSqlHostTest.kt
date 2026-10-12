package com.luma.lifeos.data.search

import com.luma.lifeos.domain.search.SearchQuery
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/** Executes the actual generated SQL against host SQLite and the exported Room schema. */
class SearchSqlHostTest {
    @Test fun generatedQueriesMatchLiteralTitlesNamesDatesAndCombinedFilters() {
        fun id(n: Long) = UUID(0, n)
        val utc = ZoneId.of("UTC")
        val queries = linkedMapOf(
            "all" to SearchQuery(), "wildcard" to SearchQuery("%_"), "quote" to SearchQuery("'quote'"),
            "slash" to SearchQuery("\\path"), "person" to SearchQuery("آرش"), "tag" to SearchQuery("weekEND"),
            "place" to SearchQuery("park"), "body" to SearchQuery("private-secret"), "note" to SearchQuery("catalog-secret"),
            "injection" to SearchQuery("%' OR 1=1 --"), "journalTitle" to SearchQuery("Child title"),
            "missingPerson" to SearchQuery(personIds = setOf(id(999))),
            "combined" to SearchQuery("walk", LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-12"), setOf(id(101), id(102)), setOf(id(201), id(202)), id(301)),
            "benchTitle" to SearchQuery("Fixture 49"), "benchMissing" to SearchQuery("Absent name"),
            "benchDate" to SearchQuery(from = LocalDate.parse("2026-10-01")),
            "benchCombined" to SearchQuery("Fixture", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-12"),
                setOf(id(101)), setOf(id(201)), id(301)),
        )
        val file = File.createTempFile("luma-search-sql-", ".tsv")
        fun encode(text: String) = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        try {
            file.writeText(queries.entries.joinToString("\n") { (name, query) ->
                val sql = SearchSql(query, utc)
                listOf(name, encode(sql.countSql), encode(sql.pageSql),
                    sql.arguments.joinToString(",") { if (it is Number) "n$it" else "s${encode(it.toString())}" }).joinToString("\t")
            })
            val script = requireNotNull(javaClass.getResource("/search_sql_check.py"))
            val runner = File.createTempFile("luma-search-check-", ".py")
            try {
                runner.writeText(script.readText())
                val schema = File("schemas/com.luma.lifeos.data.db.LumaDatabase/2.json").absolutePath
                val process = ProcessBuilder("python3", runner.absolutePath, file.absolutePath, schema).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                val exit = process.waitFor()
                println(output)
                assertEquals(output, 0, exit)
            } finally { runner.delete() }
        } finally { file.delete() }
    }
}
