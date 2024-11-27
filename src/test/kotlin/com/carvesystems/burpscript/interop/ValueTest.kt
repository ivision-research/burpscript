package com.carvesystems.burpscript.interop

import com.carvesystems.burpscript.internal.testing.matchers.value.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.graalvm.polyglot.Context

class ValueTest : StringSpec() {
    private lateinit var ctx: Context

    init {

        beforeSpec {
            ctx = Context.newBuilder().allowAllAccess(true).build()
        }

        "asNumber" {
            ctx.asValue(Int.MAX_VALUE).shouldBe(Int.MAX_VALUE)
            ctx.asValue(Long.MAX_VALUE).shouldBe(Long.MAX_VALUE)
            ctx.asValue(42.0).shouldBe(42.0)
        }

        "String asBinaryArg" {
            ctx.asValue(
                "AQID"
            ).asBinaryArg().shouldBe(
                byteArrayOf(1, 2, 3)
            )
        }

        "iterable asBinaryArg" {
            ctx.asValue(
                listOf(1, 2, 3) as Iterable<Int>
            ).asBinaryArg().shouldBe(
                byteArrayOf(1, 2, 3)
            )
        }

        "toException" {
            ctx.asValue(Exception("test")).toException()!!.message.shouldBe("test")

            val pyCtx = Context.newBuilder("python").allowAllAccess(true).build()
            val pyException = pyCtx.eval("python", "Exception('test')")
            pyException.toException()!!.message.shouldBe("Exception: test")

            val jsCtx = Context.newBuilder("js").allowAllAccess(true).build()
            val jsException = jsCtx.eval("js", "new Error('test')")
            jsException.toException()!!.message.shouldBe("Error: test")
        }
    }
}

