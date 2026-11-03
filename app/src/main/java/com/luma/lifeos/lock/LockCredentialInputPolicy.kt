package com.luma.lifeos.lock

/** Method-specific canonicalization. The caller keeps ownership of [input] and returned arrays. */
object LockCredentialInputPolicy {
    fun canonicalize(type: LockCredentialType, input: CharArray): CharArray? = when (type) {
        LockCredentialType.PIN -> canonicalizePin(input)
        LockCredentialType.PASSWORD -> canonicalizePassword(input)
        LockCredentialType.PATTERN -> canonicalizePattern(input)
    }

    private fun canonicalizePin(input: CharArray): CharArray? {
        if (input.size !in MIN_CREDENTIAL_LENGTH..MAX_CREDENTIAL_LENGTH) return null
        val canonical = CharArray(input.size)
        for (index in input.indices) {
            val digit = when (val value = input[index].code) {
                in ASCII_DIGITS -> value - ASCII_DIGITS.first
                in PERSIAN_DIGITS -> value - PERSIAN_DIGITS.first
                in ARABIC_INDIC_DIGITS -> value - ARABIC_INDIC_DIGITS.first
                else -> {
                    canonical.fill('\u0000')
                    return null
                }
            }
            canonical[index] = ('0'.code + digit).toChar()
        }
        return canonical
    }

    private fun canonicalizePassword(input: CharArray): CharArray? {
        var codePoints = 0
        var index = 0
        while (index < input.size) {
            when {
                Character.isHighSurrogate(input[index]) -> {
                    if (index + 1 >= input.size || !Character.isLowSurrogate(input[index + 1])) return null
                    index += 2
                }
                Character.isLowSurrogate(input[index]) -> return null
                else -> index++
            }
            codePoints++
            if (codePoints > MAX_CREDENTIAL_LENGTH) return null
        }
        return if (codePoints in MIN_CREDENTIAL_LENGTH..MAX_CREDENTIAL_LENGTH) input.copyOf() else null
    }

    private fun canonicalizePattern(input: CharArray): CharArray? {
        if (input.size !in MIN_PATTERN_LENGTH..MAX_PATTERN_LENGTH) return null
        val nodes = ArrayList<Int>(input.size)
        return try {
            for (value in input) {
                if (value !in '0'..'8') return null
                nodes += value.code - '0'.code
            }
            LockPatternRules.encode(nodes)
        } finally {
            nodes.clear()
        }
    }

    private val ASCII_DIGITS = '0'.code..'9'.code
    private val PERSIAN_DIGITS = 0x06F0..0x06F9
    private val ARABIC_INDIC_DIGITS = 0x0660..0x0669
    private const val MIN_CREDENTIAL_LENGTH = 4
    private const val MAX_CREDENTIAL_LENGTH = 32
    private const val MIN_PATTERN_LENGTH = 4
    private const val MAX_PATTERN_LENGTH = 9
}
