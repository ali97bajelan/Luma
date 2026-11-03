package com.luma.lifeos.lock

/** Canonical ordering and encoding for the fixed, physically left-to-right 3x3 pattern grid. */
object LockPatternRules {
    fun appendNode(current: List<Int>, next: Int): List<Int> {
        require(next in NODE_IDS) { "Pattern node is outside the 3x3 grid" }
        require(current.size <= MAX_NODES && current.all { it in NODE_IDS } && current.distinct().size == current.size) {
            "Current pattern is not a valid node sequence"
        }
        if (next in current) return current
        if (current.isEmpty()) return listOf(next)

        val previous = current.last()
        val deltaColumn = next % GRID_SIZE - previous % GRID_SIZE
        val deltaRow = next / GRID_SIZE - previous / GRID_SIZE
        val midpoint = if (deltaColumn % 2 == 0 && deltaRow % 2 == 0) {
            ((previous / GRID_SIZE + next / GRID_SIZE) / 2) * GRID_SIZE +
                ((previous % GRID_SIZE + next % GRID_SIZE) / 2)
        } else {
            null
        }
        val insertMidpoint = midpoint != null && midpoint != previous && midpoint != next && midpoint !in current
        val resultingSize = current.size + 1 + (if (insertMidpoint) 1 else 0)
        if (resultingSize > MAX_NODES) return current

        return buildList(current.size + 2) {
            addAll(current)
            if (insertMidpoint) {
                add(checkNotNull(midpoint))
            }
            add(next)
        }
    }

    /** Returns an owned ASCII encoding only when [nodes] already includes all canonical midpoints. */
    fun encode(nodes: List<Int>): CharArray? {
        if (nodes.size !in MIN_NODES..MAX_NODES || nodes.any { it !in NODE_IDS } ||
            nodes.distinct().size != nodes.size
        ) return null

        var replay = listOf(nodes.first())
        for (index in 1 until nodes.size) replay = appendNode(replay, nodes[index])
        if (replay != nodes) return null
        return CharArray(nodes.size) { index -> ('0'.code + nodes[index]).toChar() }
    }

    private const val GRID_SIZE = 3
    private const val MIN_NODES = 4
    private const val MAX_NODES = 9
    private val NODE_IDS = 0..8
}
