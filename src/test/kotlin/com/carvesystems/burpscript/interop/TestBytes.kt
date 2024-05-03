package com.carvesystems.burpscript.interop

import io.kotest.core.spec.style.StringSpec
import org.graalvm.polyglot.Context
import io.kotest.matchers.*
import io.kotest.matchers.collections.shouldContainExactly

class TestBytes : StringSpec() {
    private lateinit var ctx: Context

    init {

        beforeSpec {
            ctx = Context.newBuilder().allowAllAccess(true).build()
        }

        "Value.asByteUnsigned" {
            ctx.asValue(0xFF).asByteUnsigned().shouldBe(-1)
        }

        "Value.toByteArray with array" {
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

        "Value.toByteArray with iterator" {
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

        "Value.toByteArray with iterable" {
            ctx.asValue(
                listOf(1, 2, 3) as Iterable<Int>
            ).toByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                listOf(0xFF, 0xFE, 0xFD) as Iterable<Int>
            ).toByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toBurpByteArray with array" {
            ctx.asValue(
                arrayOf(1, 2, 3)
            ).toBurpByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                arrayOf(0xFF, 0xFE, 0xFD)
            ).toBurpByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toBurpByteArray with iterator" {
            ctx.asValue(
                listOf(1, 2, 3).iterator()
            ).toBurpByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                listOf(0xFF, 0xFE, 0xFD).iterator()
            ).toBurpByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }

        "Value.toBurpByteArray with iterable" {
            ctx.asValue(
                listOf(1, 2, 3) as Iterable<Int>
            ).toBurpByteArray().toList().shouldContainExactly(listOf(1, 2, 3))
            ctx.asValue(
                listOf(0xFF, 0xFE, 0xFD) as Iterable<Int>
            ).toBurpByteArray().toList().shouldContainExactly(
                listOf(-1, -2, -3)
            )
        }
    }
}

