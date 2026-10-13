package com.luma.lifeos.data.backup

import java.io.*
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Version 1 typed TSV; no locale-sensitive formatting or executable SQL. */
object BackupRows {
    const val MAX_LINE_BYTES = 4 * 1024 * 1024
    fun write(output: OutputStream, columns: List<String>, rows: Sequence<List<Any?>>) {
        val writer = output.bufferedWriter(Charsets.UTF_8)
        writer.write(columns.joinToString("\t")); writer.newLine()
        rows.forEach { row ->
            require(row.size == columns.size)
            val line = row.joinToString("\t") { value -> when (value) {
                null -> "N"
                is Long -> "I$value"
                is Double -> { require(value.isFinite()); "F$value" }
                is String -> "S" + Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
                else -> error("Unsupported backup cell")
            } }
            require(line.length <= MAX_LINE_BYTES) { "Backup row is too large" }
            writer.write(line); writer.newLine()
        }
        writer.flush()
    }
    fun read(file: File, columns: List<String>, maxLineBytes: Int = MAX_LINE_BYTES, onRow: (List<Any?>) -> Unit) {
        file.inputStream().buffered().use { input ->
            require(readLine(input, maxLineBytes) == columns.joinToString("\t")) { "Backup columns do not match" }
            while (true) {
                val line = readLine(input, maxLineBytes) ?: break
                val cells = line.split('\t')
                require(cells.size == columns.size) { "Backup row width does not match" }
                onRow(cells.map { cell ->
                    require(cell.isNotEmpty())
                    when (cell[0]) {
                        'N' -> { require(cell == "N"); null }
                        'I' -> cell.substring(1).toLong()
                        'F' -> cell.substring(1).toDouble().also { require(it.isFinite()) }
                        'S' -> Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(cell.substring(1)))).toString()
                        else -> error("Unknown backup cell type")
                    }
                })
            }
        }
    }
    private fun readLine(input: InputStream, limit: Int): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) { require(bytes.size() == 0) { "Truncated backup row" }; return null }
            if (value == 10) return bytes.toString("UTF-8")
            require(value < 128 && bytes.size() < limit) { "Invalid or oversized backup row" }
            bytes.write(value)
        }
    }
}
