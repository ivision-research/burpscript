package com.carvesystems.burpscript.interop

import com.carvesystems.burpscript.burpUtils
import burp.api.montoya.core.ByteArray as BurpByteArray
import org.graalvm.polyglot.Value


fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun ByteArray.toBurpByteArray(): BurpByteArray = BurpByteArray.byteArray(*this)

fun BurpByteArray.asString(): String = burpUtils.byteUtils().convertToString(bytes)

fun BurpByteArray.toByteArray(): ByteArray = ByteArray(length()) { idx ->
    getByte(idx)
}

fun burpByteArrayOf(vararg bytes: Byte): BurpByteArray = BurpByteArray.byteArray(*bytes)


/**
 * Convert an unsigned byte value to a signed Byte
 */
fun Value.asByteUnsigned(): Byte = asInt().toByte()


/**
 * Convert iterable integers to ByteArray. Unsigned byte values (e.g. > 127) are converted to signed
 */
fun Value.toByteArray(): ByteArray {
    if (hasArrayElements()) {
        return ByteArray(arraySize.toInt()) { idx ->
            val value = getArrayElement(idx.toLong())
            value.asByteUnsigned()
        }
    } else if (hasIterator()) {
        val lst = mutableListOf<Byte>()
        val it = iterator
        while (it.hasIteratorNextElement()) {
            val next = it.iteratorNextElement
            lst.add(next.asByteUnsigned())
        }
        return lst.toByteArray()
    } else if (isIterator) {
        val lst = mutableListOf<Byte>()
        while (hasIteratorNextElement()) {
            val next = iteratorNextElement
            lst.add(next.asByteUnsigned())
        }
        return lst.toByteArray()
    }
    throw IllegalArgumentException("can't make a ByteArray from type $this")
}

fun Value.toBurpByteArray(): BurpByteArray {
    if (hasArrayElements()) {
        return BurpByteArray.byteArrayOfLength(arraySize.toInt()).also {
            for (idx in 0 until arraySize) {
                val value = getArrayElement(idx)
                it.setByte(idx.toInt(), value.asByteUnsigned())
            }
        }
    } else if (hasIterator()) {
        val lst = mutableListOf<Byte>()
        val it = iterator
        while (it.hasIteratorNextElement()) {
            val next = it.iteratorNextElement
            lst.add(next.asByteUnsigned())
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
            lst.add(next.asByteUnsigned())
        }
        return BurpByteArray.byteArrayOfLength(lst.size).also {
            for (idx in lst.indices) {
                it.setByte(idx, lst[idx])
            }
        }
    }
    throw IllegalArgumentException("can't make a Montoya ByteArray from type $this")
}
