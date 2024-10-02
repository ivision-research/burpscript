/**
 * Kotlin Bytes are signed. Guest bytes are, by convention, and necessity (python),
 * unsigned.
 *
 * Notes:
 *  - Polyglot does not have the in-built mechanics to automatically convert the
 *    experimental UByteArray to Value, so this cannot be used.
 *  - Polyglot Proxy objects are intended to let hosts define their own guest types.
 *    These have limitations. You cannot define a ProxyArray, such that, e.g. a python
 *    guest thinks that it is a `bytes` object. Additionally, by design, proxy types
 *    are not automatically mapped to host types, which perhaps means they cannot be
 *    passed back into a host function?
 *    https://github.com/oracle/graal/issues/2139#issuecomment-1904152100
 *  - At any rate, the best we can do given the limited ability to define custom type
 *    mapping is to explicitly convert between host (signed) and guest (unsigned) bytes
 *    at the script api boundaries we control.
 *
 *  - A targetTypeMapping would at least allow host functions to accept unsigned bytes
 *  - https://github.com/oracle/graal/issues/2118
 */

package com.carvesystems.burpscript.interop

import burp.api.montoya.core.ByteArray as BurpByteArray
import org.graalvm.polyglot.Value
import java.util.*

/**
 * Annotates a return type that is to be interpreted by guest languages as an iterable
 * of unsigned bytes.
 *
 * Intended to be converted by guests to their native byte array type.
 *
 * Python:
 *  bytes(unsigned_byte_array)
 *
 * JavaScript:
 *  Uint8Array(unsignedByteArray)
 */
typealias UnsignedByteArray = Array<Int>

/**
 * Annotates an argument type passed in from a guest language, which is
 * interpreted, by the host, as binary data.
 *
 * Allowed inputs:
 * - A string containing a base64-encoded binary blob ("aGVsbG8K").
 * - A string containing a hex-encoded binary blob ("68656c6c6f").
 * - An iterable of unsigned integer values. This can be a guest
 *   language array, list, iterator, [bytes] (python), etc.
 *
 */
typealias AnyBinary = Value

/**
 * Convert or decode a Value from a guest type to a ByteArray.
 *
 * - If the Value is a string, it is decoded as a base64 or hex-encoded
 *   binary blob.
 * - If the Value is an iterable, it is converted to a ByteArray by
 *   interpreting integer values as unsigned bytes.
 */
fun Value.asAnyBinaryToByteArray(): ByteArray =
    if (isString) {
        asString().decodeAsByteArray()
    } else {
        toByteArray()
    }

/**
 * Reinterpret a guest unsigned byte value (e.g. > 127) to a host signed byte
 */
fun Value.reinterpretAsSignedByte(): Byte = asInt().toByte()

/**
 * Value
 */

/**
 * Convert guest iterable types to ByteArray, reinterpreting unsigned to signed
 */
fun Value.toByteArray(): ByteArray {
    if (hasArrayElements()) {
        return ByteArray(arraySize.toInt()) { idx ->
            val value = getArrayElement(idx.toLong())
            value.reinterpretAsSignedByte()
        }
    } else if (hasIterator()) {
        val lst = mutableListOf<Byte>()
        val it = iterator
        while (it.hasIteratorNextElement()) {
            val next = it.iteratorNextElement
            lst.add(next.reinterpretAsSignedByte())
        }
        return lst.toByteArray()
    } else if (isIterator) {
        val lst = mutableListOf<Byte>()
        while (hasIteratorNextElement()) {
            val next = iteratorNextElement
            lst.add(next.reinterpretAsSignedByte())
        }
        return lst.toByteArray()
    }
    throw IllegalArgumentException("can't make a ByteArray from type $this")
}

/**
 * Convert guest iterable types to montoya ByteArray, reinterpreting unsigned to signed
 */
fun Value.toBurpByteArray(): BurpByteArray {
    if (hasArrayElements()) {
        return BurpByteArray.byteArrayOfLength(arraySize.toInt()).also {
            for (idx in 0 until arraySize) {
                val value = getArrayElement(idx)
                it.setByte(idx.toInt(), value.reinterpretAsSignedByte())
            }
        }
    } else if (hasIterator()) {
        val lst = mutableListOf<Byte>()
        val it = iterator
        while (it.hasIteratorNextElement()) {
            val next = it.iteratorNextElement
            lst.add(next.reinterpretAsSignedByte())
        }
        return BurpByteArray.byteArrayOfLength(lst.size).also {
            for (idx in lst.indices) {
                it.setByte(idx, lst[idx])
            }
        }
    } else if (isIterator) {
        val lst = mutableListOf<Byte>()
        while (hasIteratorNextElement()) {
            val next = iteratorNextElement
            lst.add(next.reinterpretAsSignedByte())
        }
        return BurpByteArray.byteArrayOfLength(lst.size).also {
            for (idx in lst.indices) {
                it.setByte(idx, lst[idx])
            }
        }
    }
    throw IllegalArgumentException("can't make a Montoya ByteArray from type $this")
}

/**
 * ByteArray
 */

fun ByteArray.toUnsignedByteArray(): UnsignedByteArray =
    map { it.toUByte().toInt() }.toTypedArray()

fun ByteArray.toBurpByteArray(): BurpByteArray =
    BurpByteArray.byteArray(*this)

fun ByteArray.toHex(): String =
    joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

/**
 * Montoya ByteArray
 */

fun BurpByteArray.toByteArray(): ByteArray = ByteArray(length()) { idx ->
    getByte(idx)
}

fun BurpByteArray.toUnsignedByteArray(): UnsignedByteArray =
    Array<Int>(length()) { idx -> getByte(idx).toUByte().toInt() }

fun burpByteArrayOf(vararg bytes: Byte): BurpByteArray = BurpByteArray.byteArray(*bytes)

/**
 * String
 */

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

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)

fun String.isHex(): Boolean = this.lowercase().all {
    it in HEX_CHARS
}

/**
 * Decode a string containing binary hex, or base64 data into a byte array
 */
fun String.decodeAsByteArray(): ByteArray =
    try {
        this.decodeHex()
    } catch (e: IllegalArgumentException) {
        this.decodeBase64()
    }
