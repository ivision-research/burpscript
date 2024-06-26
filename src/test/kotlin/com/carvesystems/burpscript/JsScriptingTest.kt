package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.fromJson
import com.carvesystems.burpscript.matchers.value.shouldContainExactly
import com.carvesystems.burpscript.matchers.value.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import kotlin.io.path.writeText


class JsScriptingTest : StringSpec() {
    private lateinit var ctx: Context

    companion object {
        const val TEST_FUNC = "test_func"
    }

    private fun exec(script: String, vararg args: Any): Value {
        val src = Source.newBuilder("js", script, "test-script.mjs").build()
        val parsed = ctx.eval(src)
        parsed.hasMember(TEST_FUNC).shouldBeTrue()
        val func = parsed.getMember(TEST_FUNC)
        return func.execute(*args)
    }

    init {
        beforeSpec {
            ctx = JsContextBuilder().withBindings("helpers" to ScriptHelpers()).build()
        }

        "can use json like object" {
            val script = """
                export function $TEST_FUNC(req) {
                   const obj = req.bodyToJson();
                   obj["obj"]["key"] = "modified";
                   obj["obj"]["new"] = "value";
                   return req.withJson(obj);
                }
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
                        "key": "value"
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
                    "foo" to "bar", "obj" to mapOf(
                        "key" to "modified", "new" to "value"
                    )
                )
            )
        }
    }
}

class JsContextTest : StringSpec() {
    init {
        "import" {
            tempdir() { importPath ->
                val ctx = JsContextBuilder().withImportPath(importPath).build()

                val toImportMjs = importPath.resolve("common.mjs")
                toImportMjs.writeText(
                    """
                    export function doSomething() {
                        return "did something";
                    }
                """.trimIndent()
                )

                val import = """
                import { doSomething } from './${toImportMjs.fileName}';
                export { doSomething };
            """.trimIndent()
                val mod = ctx.eval(Source.newBuilder("js", import, "test.mjs").build())
                mod.hasMember("doSomething").shouldBeTrue()
                mod.getMember("doSomething").execute().shouldBe("did something")
            }
        }

        "require" {
            tempdir() { importPath ->
                val toRequireCJs = importPath.resolve("common.js")
                toRequireCJs.writeText(
                    """
                    function doSomething() {
                        return "did something";
                    }
                    module.exports = { doSomething };
                """.trimIndent()
                )

                val ctx = JsContextBuilder().withImportPath(importPath).build()

                val require = """
                    const { doSomething } = require('./${toRequireCJs.fileName}');
                    module.exports = { doSomething };
                """.trimIndent()
                val mod = ctx.eval(Source.newBuilder("js", require, "test.js").build())
                mod.hasMember("doSomething").shouldBeTrue()
                mod.getMember("doSomething").execute().shouldBe("did something")
            }
        }
    }
}

class JsBindingsTest : StringSpec() {
    init {
        "console" {
            val logger = mockk<ScriptLogger>()
            val log = slot<Value>()
            val error = slot<Value>()
            val debug = slot<Value>()
            every {
                logger.info(capture(log))
            } returns Unit
            every {
                logger.error(capture(error))
            } returns Unit
            every {
                logger.debug(capture(debug))
            } returns Unit

            val ctx = JsContextBuilder().withConsoleLogger(logger).build()
            val script = """
                console.log("log");
                console.error("error");
                console.debug("debug");
            """.trimIndent()
            ctx.eval("js", script)
            verify {
                logger.info(capture(log))
            }
            log.captured.shouldBe("log")
            error.captured.shouldBe("error")
            debug.captured.shouldBe("debug")
        }
    }
}
