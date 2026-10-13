package com.luma.lifeos.data.reliability

import androidx.sqlite.db.SupportSQLiteDatabase
import com.luma.lifeos.data.db.LumaDatabase
import java.io.File
import java.lang.reflect.Proxy
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Host SQL coverage complements, but does not replace, native Room tests. */
class ReliabilitySqlHostTest {
    @Test fun registeredMigrationPreservesEveryVersionOneTableAndRelationship() {
        val statements = mutableListOf<String>()
        // Capture the real migration's database boundary, then execute its SQL on SQLite.
        val connection = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            check(method.name == "execSQL" && args?.size == 1) { "Unexpected migration operation: $method" }
            statements += args!![0] as String
            null
        } as SupportSQLiteDatabase
        LumaDatabase.MIGRATIONS.single { it.startVersion == 1 && it.endVersion == 2 }.migrate(connection)
        val sql = File.createTempFile("luma-migration-", ".txt")
        try {
            sql.writeText(statements.joinToString("\n") {
                Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
            })
            runScript("migration_graph_check.py", sql.absolutePath, schema(1), schema(2))
        } finally { sql.delete() }
    }

    @Test fun generatedTimelineQueriesBoundSortAndCountMixedMultiYearHistory() {
        // Read compiler output rather than maintaining a second copy of DAO SQL.
        val generated = File("build/generated/ksp/debug/kotlin/com/luma/lifeos/data/dao/LifeEventDao_Impl.kt")
        check(generated.isFile) { "Compile core:data before running SQL tests" }
        runScript("timeline_sql_check.py", generated.absolutePath, schema(2))
    }

    private fun schema(version: Int) = File("schemas/com.luma.lifeos.data.db.LumaDatabase/$version.json").absolutePath

    private fun runScript(resource: String, vararg arguments: String) {
        val runner = File.createTempFile("luma-reliability-", ".py")
        val output = File.createTempFile("luma-reliability-output-", ".txt")
        try {
            runner.writeText(requireNotNull(javaClass.getResource("/$resource")).readText())
            val process = ProcessBuilder(listOf("python3", runner.absolutePath) + arguments)
                .redirectErrorStream(true).redirectOutput(output).start()
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor()
                error("SQL regression timed out: ${output.readText()}")
            }
            val result = output.readText()
            println(result)
            assertEquals(result, 0, process.exitValue())
        } finally { runner.delete(); output.delete() }
    }
}
