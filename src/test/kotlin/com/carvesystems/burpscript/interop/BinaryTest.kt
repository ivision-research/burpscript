package com.carvesystems.burpscript.interop

import com.carvesystems.burpscript.JsContextBuilder
import com.carvesystems.burpscript.PythonContextBuilder
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.graalvm.polyglot.Context

class BinaryValueTest : StringSpec() {
    private lateinit var ctx: Context

    init {
        beforeSpec {
            ctx = Context.newBuilder().allowAllAccess(true).build()
        }

        "Value.reinterpretAsSignedByte" {
            ctx.asValue(0xFF).reinterpretAsSignedByte().shouldBe(-1)
        }

        "Value.asAnyBinaryToByteArray decodes base64 and bin hex to binary" {
            ctx.asValue(
                "AQID"
            ).asAnyBinaryToByteArray().shouldBe(
                byteArrayOf(1, 2, 3)
            )
            ctx.asValue(
                "FFFEFD"
            ).asAnyBinaryToByteArray().shouldBe(
                byteArrayOf(-1, -2, -3)
            )
        }

        "Value.asAnyBinaryToByteArray converts arrays and iterables to signed host bytes" {
            ctx.asValue(
                byteArrayOf(1, 2, 3)
            ).asAnyBinaryToByteArray().shouldBe(
                byteArrayOf(1, 2, 3)
            )
            ctx.asValue(
                arrayOf(0xFF, 0xFE, 0xFD)
            ).asAnyBinaryToByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
            ctx.asValue(
                listOf(1, 2, 3) as Iterable<Int>
            ).asAnyBinaryToByteArray().shouldBe(
                byteArrayOf(1, 2, 3)
            )
        }

        "Value.toByteArray converts arrays to signed host bytes" {
            ctx.asValue(
                arrayOf(1, 2, 3)
            ).toByteArray().toList().shouldContainExactly(
                listOf(1, 2, 3)
            )
            ctx.asValue(
                arrayOf(0xFF, 0xFE, 0xFD)
            ).toByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toByteArray converts iterators to signed host bytes" {
            ctx.asValue(
                listOf(1, 2, 3).iterator()
            ).toByteArray().toList().shouldContainExactly(
                listOf(1, 2, 3)
            )
            ctx.asValue(
                listOf(0xFF, 0xFE, 0xFD).iterator()
            ).toByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toByteArray converts iterables to signed host bytes" {
            ctx.asValue(
                listOf(1, 2, 3) as Iterable<Int>
            ).toByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                listOf(0xFF, 0xFE, 0xFD) as Iterable<Int>
            ).toByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toBurpByteArray converts arrays to signed burp bytes" {
            ctx.asValue(
                arrayOf(1, 2, 3)
            ).toBurpByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                arrayOf(0xFF, 0xFE, 0xFD)
            ).toBurpByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toBurpByteArray converts iterators to signed burp bytes" {
            ctx.asValue(
                listOf(1, 2, 3).iterator()
            ).toBurpByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                listOf(0xFF, 0xFE, 0xFD).iterator()
            ).toBurpByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toBurpByteArray converts iterables to signed burp bytes" {
            ctx.asValue(
                listOf(1, 2, 3) as Iterable<Int>
            ).toBurpByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                listOf(0xFF, 0xFE, 0xFD) as Iterable<Int>
            ).toBurpByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "ByteArray converts to guest unsigned bytes" {
            byteArrayOf(1, 2, 3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(1, 2, 3)
            )
            byteArrayOf(-1, -2, -3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(0xFF, 0xFE, 0xFD)
            )
        }

        "BurpByteArray converts to guest unsigned bytes" {
            burpByteArrayOf(1, 2, 3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(1, 2, 3)
            )
            burpByteArrayOf(-1, -2, -3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(0xFF, 0xFE, 0xFD)
            )
        }
    }
}

class BinaryByteArrayTest : StringSpec() {
    init {
        "ByteArray converts to guest unsigned bytes" {
            byteArrayOf(1, 2, 3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(1, 2, 3)
            )
            byteArrayOf(-1, -2, -3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(0xFF, 0xFE, 0xFD)
            )
        }
    }
}

class BinaryBurpByteArrayTest : StringSpec() {
    init {
        "BurpByteArray converts to guest unsigned bytes" {
            burpByteArrayOf(1, 2, 3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(1, 2, 3)
            )
            burpByteArrayOf(-1, -2, -3).toUnsignedByteArray().toList().shouldContainExactly(
                listOf(0xFF, 0xFE, 0xFD)
            )
        }
    }
}

class BinaryStringTest : StringSpec() {
    init {
        "isHex" {
            "0123456789abcdef".isHex().shouldBeTrue()
            "0123456789ABCDEF".isHex().shouldBeTrue()
            "".isHex().shouldBeTrue() // Is it?
            "0123456789ABCDEFG".isHex().shouldBeFalse()
        }

        "decodeHex" {
            "00010203040506070809".decodeHex().shouldBe(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
            "".decodeHex().shouldBe(byteArrayOf())
        }

        "unsigned decodeHex" {
            "7F808182".decodeHex().shouldBe(byteArrayOf(127, -128, -127, -126))
        }

        "hex decodeAsByteArray" {
            "00010203040506070809".decodeAsByteArray().shouldBe(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
            "".decodeAsByteArray().shouldBe(byteArrayOf())
        }

        "base64 decodeAsByteArray" {
            "AH+A/w==".decodeAsByteArray().shouldBe(byteArrayOf(0, 127, -128, -1))
        }
    }
}

class BinaryPythonInteropTest : StringSpec() {
    private lateinit var ctx: Context

    init {
        beforeSpec {
            ctx = PythonContextBuilder().build()
        }

        "python bytes to host bytes" {
            val script = """b'\x00\x7f\x80\xff'"""

            val ret = ctx.eval("python", script)
            val hostBytes = ret.toByteArray()
            hostBytes.shouldBe(byteArrayOf(0x00, 0x7F, -128, -1))
        }

        "host bytes to python bytes and back to host bytes" {
            //
            // guest bytes are not really python "bytes", but this
            // at least validates that if we pass in a guest bytes
            // it can be converted to a native python bytes array.
            //
            val script = """
            bytes
            """.trimIndent()

            val func = ctx.eval("python", script)
            val ret = func.execute(byteArrayOf(0x00, 0x7F, -128, -1).toUnsignedByteArray())
            val hostBytes = ret.toByteArray()
            hostBytes.shouldBe(byteArrayOf(0x00, 0x7F, -128, -1))
        }
    }
}

class BinaryJsInteropTest : StringSpec() {
    private lateinit var ctx: Context

    init {
        beforeSpec {
            ctx = JsContextBuilder().build()
        }

        "js Uint8Array to host bytes" {
            val script = """
            Uint8Array.of(0x00, 0x7F, 0x80, 0xFF)
            """.trimIndent()

            val ret = ctx.eval("js", script)
            val hostBytes = ret.toByteArray()
            hostBytes.shouldBe(byteArrayOf(0x00, 0x7F, -128, -1))
        }

        "host bytes to js bytes and back to host bytes" {
            val script = """
            (function(bs) {
                return new Uint8Array(bs);
            })
            """.trimIndent()

            val func = ctx.eval("js", script)
            val ret = func.execute(byteArrayOf(0x00, 0x7F, -128, -1).toUnsignedByteArray())
            val hostBytes = ret.toByteArray()
            hostBytes.shouldBe(byteArrayOf(0x00, 0x7F, -128, -1))
        }
    }
}
