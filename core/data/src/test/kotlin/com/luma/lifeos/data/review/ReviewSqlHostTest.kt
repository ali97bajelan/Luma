package com.luma.lifeos.data.review

import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.model.EventSource
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSqlHostTest {
    @Test fun productionMetricSourceQueriesSelectExactStoredInputsAndBoundPages() {
        val dates = ReviewDates(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-14"))
        val metrics = ReviewMetricKind.entries.associate { kind -> kind.name to ReviewMetric(kind,
            id = if (kind in listOf(ReviewMetricKind.EMOTION, ReviewMetricKind.PERSON, ReviewMetricKind.PLACE)) UUID(0, 100) else null,
            activity = if (kind == ReviewMetricKind.ACTIVITY) ReviewActivityKey("steps", "count", EventSource.MANUAL) else null) } +
            ("INJECTION" to ReviewMetric(ReviewMetricKind.ACTIVITY, activity = ReviewActivityKey("steps' OR 1=1 --", "count", EventSource.MANUAL)))
        val file = File.createTempFile("luma-review-sql-", ".tsv")
        val runner = File.createTempFile("luma-review-check-", ".py")
        fun encode(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
        try {
            file.writeText(metrics.entries.joinToString("\n") { (name, metric) ->
                val sql = ReviewSql(dates, ZoneId.of("UTC"), metric)
                listOf(name, encode(sql.countSql), encode(sql.pageSql), sql.arguments.joinToString(",") {
                    if (it is Number) "n$it" else "s${encode(it.toString())}"
                }).joinToString("\t")
            })
            runner.writeText(requireNotNull(javaClass.getResource("/review_sql_check.py")).readText())
            val schema = File("schemas/com.luma.lifeos.data.db.LumaDatabase/2.json").absolutePath
            val process = ProcessBuilder("python3", runner.absolutePath, file.absolutePath, schema).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            println(output); assertEquals(output, 0, process.waitFor())
        } finally { file.delete(); runner.delete() }
    }
}
