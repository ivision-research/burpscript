package com.carvesystems.burpscript.interop

import java.util.*

private val HEX_CHARS = "0123456789abcdef"
private fun hexCharIdx(c: Char): Int {
    for (i in 0.until(16)) {
        if (c == HEX_CHARS[i]) {
            return i
        }
    }
    throw IllegalArgumentException("couldn't find $c in $HEX_CHARS")
}

fun String.decodeHex(): ByteArray {
    if (this.length.and(1) != 0) {
        throw IllegalArgumentException("length of $this is not divisible by 2")
    }
    val b = ByteArray(this.length / 2)
    val lc = this.lowercase()

    for ((j, i) in lc.indices.step(2).withIndex()) {
        val idx = hexCharIdx(lc[i])
        val idx2 = hexCharIdx(lc[i + 1])
        b[j] = idx.and(0x0F).shl(4).or(idx2.and(0x0F)).toByte()
    }
    return b

}

fun String.isHex(): Boolean = this.lowercase().all {
    it in HEX_CHARS
}

fun String.asByteArray(): ByteArray =
    try {
        this.decodeHex()
    } catch (e: IllegalArgumentException) {
        Base64.getDecoder().decode(this)
    }
