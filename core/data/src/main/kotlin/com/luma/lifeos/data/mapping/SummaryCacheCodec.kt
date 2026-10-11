package com.luma.lifeos.data.mapping

import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.summary.*
import com.luma.lifeos.domain.usage.AppUsageTotal
import com.luma.lifeos.domain.usage.DailyUsageReport
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, validated derived values only. Decoding failure means rebuild, never default metrics. */
internal object SummaryCacheCodec {
    fun encode(summary: DailySummary): String = JSONObject().apply {
        put("format", "luma-daily-summary-v1")
        put("records", summary.records); put("journals", summary.journals); put("memories", summary.memories)
        put("mood", summary.mood?.let { JSONObject().put("score", it.score.json())
            .put("energy", it.energy?.json() ?: JSONObject.NULL).put("stress", it.stress?.json() ?: JSONObject.NULL) } ?: JSONObject.NULL)
        put("usage", summary.usage?.let { usage -> JSONObject().put("total", usage.totalMillis)
            .put("apps", JSONArray().apply { usage.apps.forEach { put(JSONObject().put("package", it.packageName)
                .put("label", it.appLabel ?: JSONObject.NULL).put("duration", it.durationMillis)) } }) } ?: JSONObject.NULL)
        put("activities", summary.activities?.let { JSONArray().apply { it.forEach { total -> put(JSONObject()
            .put("type", total.type).put("unit", total.unit).put("source", total.source.name)
            .put("samples", total.samples).put("total", total.total.toPlainString())) } } } ?: JSONObject.NULL)
    }.toString()

    fun decode(json: String, date: LocalDate, zone: ZoneId): DailySummary {
        val root = JSONObject(json)
        require(root.string("format") == "luma-daily-summary-v1")
        val mood = root.nullable("mood") { value -> (value as JSONObject).let { MoodSummary(it.getJSONObject("score").scale(),
            it.nullable("energy") { energy -> (energy as JSONObject).scale() }, it.nullable("stress") { stress -> (stress as JSONObject).scale() }) } }
        val usage = root.nullable("usage") { value -> (value as JSONObject).let {
            val apps = it.getJSONArray("apps").objects().map { app -> AppUsageTotal(app.string("package"),
                app.nullable("label") { label -> label as String }, app.long("duration")) }
                .sortedWith(compareByDescending<AppUsageTotal> { app -> app.durationMillis }.thenBy { app -> app.packageName })
            DailyUsageReport(date, zone, apps, it.long("total"))
        } }
        val activities = root.nullable("activities") { value -> (value as JSONArray).objects().map {
            val decimal = it.string("total")
            require(decimal.length <= 1024 && decimal.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
            ActivityTotal(it.string("type"), it.string("unit"), EventSource.valueOf(it.string("source")), it.int("samples"),
                BigDecimal(decimal).stripTrailingZeros().toPlainString().toBigDecimal())
        }.sortedWith(compareBy<ActivityTotal> { it.type }.thenBy { it.unit }.thenBy { it.source.name }) }
        return DailySummary(date, zone, root.int("records"), root.int("journals"), root.int("memories"), mood, usage, activities)
    }
    private fun ScaleTotal.json() = JSONObject().put("samples", samples).put("sum", sum)
    private fun JSONObject.scale() = ScaleTotal(int("samples"), long("sum"))
    private fun JSONObject.long(key: String): Long {
        val number = get(key); require(number is Number)
        return BigDecimal(number.toString()).longValueExact()
    }
    private fun JSONObject.int(key: String) = Math.toIntExact(long(key))
    private fun JSONObject.string(key: String): String = (get(key) as String)
    private fun <T> JSONObject.nullable(key: String, read: (Any) -> T): T? {
        require(has(key)); return if (isNull(key)) null else read(get(key))
    }
    private fun JSONArray.objects(): List<JSONObject> {
        require(length() <= 100_000); return (0 until length()).map(::getJSONObject)
    }
}
