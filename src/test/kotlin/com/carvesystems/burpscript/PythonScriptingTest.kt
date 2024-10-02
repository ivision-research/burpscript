package com.carvesystems.burpscript

import burp.api.montoya.core.Annotations
import burp.api.montoya.core.ByteArray
import burp.api.montoya.core.ToolType
import burp.api.montoya.http.handler.HttpRequestToBeSent
import burp.api.montoya.internal.ObjectFactoryLocator
import com.carvesystems.burpscript.interop.fromJson
import com.carvesystems.burpscript.interop.toByteArray
import com.carvesystems.burpscript.internal.testing.matchers.value.shouldBe
import com.carvesystems.burpscript.internal.testing.matchers.value.shouldContainExactly
import com.carvesystems.burpscript.internal.testing.tempdir
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import kotlin.io.path.writeText

class PythonScriptingTest : StringSpec() {
    private lateinit var ctx: Context

    companion object {
        const val TEST_FUNCTION = "test_func"
        const val MESSAGE_ID = 1
    }

    private fun exec(script: String, vararg args: Any): Value {
        val src = Source.newBuilder("python", script, "test-script.py").build()
        val parsed = ctx.eval(src)
        parsed.hasMember(TEST_FUNCTION).shouldBeTrue()
        val value = parsed.getMember(TEST_FUNCTION)
        return value.execute(*args)
    }

    private fun mockRequest(): Pair<ScriptHttpRequest, HttpRequestToBeSent> {
        val req = mockk<HttpRequestToBeSent>(relaxed = true)
        val annotations = mockk<Annotations>(relaxed = true)

        every {
            req.annotations()
        } returns annotations

        every {
            req.messageId()
        } returns MESSAGE_ID

        every {
            req.toolSource()
        } returns SimpleToolSource(ToolType.PROXY)

        val wrapped = ScriptHttpRequestImpl.wrap(req)
        return wrapped to req
    }

    init {

        beforeSpec {
            ctx = PythonContextBuilder()
                .withBindings("helpers" to ScriptHelpers())
                .build()
        }

        "withBytes allows passing byte arrays" {
            val script = """
                |def $TEST_FUNCTION(req):
                |   return req.withBytes(b'\x00\x7f\x80\xff')
            """.trimMargin()

            val (wrapped, req) = mockRequest()
            val bytesInput = slot<ByteArray>()

            every {
                req.withBody(capture(bytesInput))
            } returns req

            ObjectFactoryLocator.FACTORY

            exec(script, wrapped)

            verify {
                req.annotations()
                req.toolSource()
                req.messageId()
                req.withBody(capture(bytesInput))
            }
            confirmVerified(req)

            val expected = byteArrayOf(0x00, 0x7F, -128, -1)

            val bytes = bytesInput.captured

            bytes.length().shouldBe(expected.size)

            bytes.forEachIndexed { idx, value ->
                value.shouldBe(expected[idx])
            }
        }

        "can pass Python arrays to withJson" {
            val script = """
                |def $TEST_FUNCTION(req):
                |   return req.withJson(
                |       [ True, None, False, { 'inner': 'value' } ]
                |   )
            """.trimMargin()

            val req = mockk<ScriptHttpRequest>()
            val value = slot<Value>()
            every {
                req.withJson(capture(value))
            } returns req

            exec(script, req)
            verify {
                req.withJson(capture(value))
            }

            val json: Value = value.captured
            json.shouldContainExactly(true, null, false, mapOf("inner" to "value"))
        }

        "can pass Python dicts to withJson" {
            val script = """
                |def $TEST_FUNCTION(req):
                |   return req.withJson({
                |       'string': 'string',
                |       'dict': {
                |           'double': 1.2,
                |           'number': 12
                |       },
                |       'arr': [ True, None, False, { 'inner': 'value' } ]
                |   })
            """.trimMargin()

            val req = mockk<ScriptHttpRequest>()
            val value = slot<Value>()
            every {
                req.withJson(capture(value))
            } returns req

            exec(script, req)
            verify {
                req.withJson(capture(value))
            }

            val json: Value = value.captured
            json.shouldContainExactly(
                mapOf(
                    "string" to "string",
                    "dict" to mapOf(
                        "double" to 1.2,
                        "number" to 12
                    ),
                    "arr" to listOf(true, null, false, mapOf("inner" to "value"))
                )
            )
        }

        "can use json like dict" {
            val script = """
                def $TEST_FUNCTION(req):
                    d = req.bodyToJson()
                    d["obj"]["key"] = "modified"
                    d["obj"]["new"] = "value"
                    return req.withJson(d)
            """.trimIndent()

            val req = mockk<ScriptHttpRequest>()
            val value = slot<Value>()
            every {
                req.bodyToJson()
            } returns fromJson(
                """
                {
                    "foo": "bar",
                    "obj": {
                        "key": "unmodified"
                    }
                }
                """.trimIndent()
            )
            every {
                req.withJson(capture(value))
            } returns req

            exec(script, req)
            verify {
                req.withJson(capture(value))
            }
            value.captured.shouldContainExactly(
                mapOf(
                    "foo" to "bar",
                    "obj" to mapOf(
                        "key" to "modified",
                        "new" to "value"
                    )
                )
            )
        }
    }
}

class PythonBindingsTest : StringSpec() {
    init {
        "print" {
            val logger = mockk<ScriptLogger>()
            val msg = slot<Value>()
            every {
                logger.info(capture(msg))
            } returns Unit

            val ctx = PythonContextBuilder().withConsoleLogger(logger).build()

            val script = """print("hello")"""
            ctx.eval("python", script)
            msg.captured.shouldBe("hello")
        }
    }
}

