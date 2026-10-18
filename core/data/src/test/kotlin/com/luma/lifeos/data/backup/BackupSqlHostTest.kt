package com.luma.lifeos.data.backup

import com.luma.lifeos.data.db.LumaDatabase
import java.io.File
import java.util.Base64
import org.junit.Test
import org.junit.Assert.assertEquals

/** Checks production SQL with the compiler-exported schema, not a hand-written lookalike. */
class BackupSqlHostTest {
    @Test fun replacementRollsBackEveryTableAndRejectsMismatchedChildren() {
        val instructions = File.createTempFile("backup-sql", ".txt")
        val script = File.createTempFile("backup-host", ".py")
        try {
            fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
            instructions.writeText(BackupSql.deleteStatements.joinToString("\n") { "D\t" + b64(it) } + "\n" +
                BackupSql.invalidGraphQueries.joinToString("\n") { "V\t" + b64(it) } + "\n" +
                "I\t" + b64(BackupSql.insert("life_event", listOf("id", "type", "occurred_at", "created_at", "updated_at", "source"))))
            script.writeText(requireNotNull(javaClass.getResource("/backup_sql_check.py")).readText())
            val schema = File("schemas/com.luma.lifeos.data.db.LumaDatabase/${LumaDatabase.VERSION}.json").absolutePath
            val process = ProcessBuilder("python3", script.path, instructions.path, schema).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
        } finally { instructions.delete(); script.delete() }
    }
}
