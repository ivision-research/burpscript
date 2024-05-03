package com.carvesystems.burpscript

import burp.api.montoya.core.ByteArray as BurpByteArray
import burp.api.montoya.core.Range
import burp.api.montoya.internal.MontoyaObjectFactory
import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.mockk
import java.util.regex.Pattern

private fun <T> co(stubBlock: MockKMatcherScope.() -> T) {
    every(stubBlock) answers { callOriginal() }
}

class SimpleArray(private val arr: kotlin.ByteArray) : BurpByteArray {
    override fun iterator(): MutableIterator<Byte> =
        arr.toMutableList().iterator()

    override fun getByte(index: Int): Byte = arr[index]

    override fun setByte(index: Int, value: Byte) {
        arr[index] = value
    }

    override fun setByte(index: Int, value: Int) {
        arr[index] = value.toByte()
    }

    override fun setBytes(index: Int, vararg data: Byte) {
        for (idx in index until index + data.size) {
            arr[idx] = data[idx]
        }
    }

    override fun setBytes(index: Int, vararg data: Int) {
        for (idx in index until index + data.size) {
            arr[idx] = data[idx].toByte()
        }
    }

    override fun setBytes(index: Int, byteArray: BurpByteArray) {
        for (idx in index until index + byteArray.length()) {
            arr[idx] = byteArray.getByte(idx)
        }
    }

    override fun length(): Int = arr.size

    override fun getBytes(): kotlin.ByteArray = arr

    override fun subArray(startIndexInclusive: Int, endIndexExclusive: Int): BurpByteArray =
        SimpleArray(arr.slice(startIndexInclusive until endIndexExclusive).toByteArray())

    override fun subArray(range: Range): BurpByteArray = subArray(range.startIndexInclusive(), range.endIndexExclusive())

    override fun copy(): BurpByteArray = SimpleArray(arr.clone())

    override fun copyToTempFile(): BurpByteArray {
        TODO("wtf")
    }

    override fun indexOf(searchTerm: BurpByteArray): Int {
        TODO("unneeded")
    }

    override fun indexOf(searchTerm: String?): Int {
        TODO("unneeded")
    }

    override fun indexOf(searchTerm: BurpByteArray?, caseSensitive: Boolean): Int {
        TODO("unneeded")
    }

    override fun indexOf(searchTerm: String?, caseSensitive: Boolean): Int {
        TODO("unneeded")
    }

    override fun indexOf(
        searchTerm: BurpByteArray?,
        caseSensitive: Boolean,
        startIndexInclusive: Int,
        endIndexExclusive: Int
    ): Int {
        TODO("unneeded")
    }

    override fun indexOf(
        searchTerm: String?,
        caseSensitive: Boolean,
        startIndexInclusive: Int,
        endIndexExclusive: Int
    ): Int {
        TODO("unneeded")
    }

    override fun indexOf(pattern: Pattern?): Int {
        TODO("unneeded")
    }

    override fun indexOf(pattern: Pattern?, startIndexInclusive: Int, endIndexExclusive: Int): Int {
        TODO("unneeded")
    }

    override fun countMatches(searchTerm: BurpByteArray?): Int {
        TODO("unneeded")
    }

    override fun countMatches(searchTerm: String?): Int {
        TODO("unneeded")
    }

    override fun countMatches(searchTerm: BurpByteArray?, caseSensitive: Boolean): Int {
        TODO("unneeded")
    }

    override fun countMatches(searchTerm: String?, caseSensitive: Boolean): Int {
        TODO("unneeded")
    }

    override fun countMatches(
        searchTerm: BurpByteArray?,
        caseSensitive: Boolean,
        startIndexInclusive: Int,
        endIndexExclusive: Int
    ): Int {
        TODO("unneeded")
    }

    override fun countMatches(
        searchTerm: String?,
        caseSensitive: Boolean,
        startIndexInclusive: Int,
        endIndexExclusive: Int
    ): Int {
        TODO("unneeded")
    }

    override fun countMatches(pattern: Pattern?): Int {
        TODO("unneeded")
    }

    override fun countMatches(pattern: Pattern?, startIndexInclusive: Int, endIndexExclusive: Int): Int {
        TODO("unneeded")
    }

    override fun withAppended(vararg data: Byte): BurpByteArray {
        TODO("unneeded")
    }

    override fun withAppended(vararg data: Int): BurpByteArray {
        TODO("unneeded")
    }

    override fun withAppended(text: String?): BurpByteArray {
        TODO("unneeded")
    }

    override fun withAppended(byteArray: BurpByteArray?): BurpByteArray {
        TODO("unneeded")
    }
}

fun mockObjectFactory(): MontoyaObjectFactory {
    val mock = mockk<MontoyaObjectFactory>(relaxed = true)
    every {
        mock.byteArrayOfLength(any())
    } answers {
        val size = it.invocation.args.first() as Int
        SimpleArray(ByteArray(size))
    }
    every {
        mock.byteArray(any<ByteArray>())
    } answers {
        val bytes = it.invocation.args.first() as ByteArray
        SimpleArray(bytes)
    }
    return mock
}