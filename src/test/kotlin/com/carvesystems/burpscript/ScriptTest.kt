package com.carvesystems.burpscript

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.util.*
import kotlin.io.path.writeText

class ScriptTest : StringSpec() {
    private lateinit var hooks: Hooks
    private lateinit var pyBuilder: PythonContextBuilder
    private lateinit var jsBuilder: JsContextBuilder

    init {
        beforeSpec {
            hooks = mockk<Hooks>()
            pyBuilder = PythonContextBuilder().apply {
                withBindings("test_hooks" to hooks)
            }
            jsBuilder = JsContextBuilder().apply {
                withBindings("testHooks" to hooks)
            }
        }

        afterTest {
            clearMocks(hooks)
        }

        "calls lifecycle hooks" {
            val src = """
                def initialize():
                    test_hooks.initialize()
                def cleanup():
                    test_hooks.cleanup()
            """.trimIndent()
            shouldRunLifecycleHooks(src, "lifecycle.py", Language.Python, pyBuilder)
        }

        "calls addon lifecycle hooks" {
            val src = """
                class Addon:
                    def initialize(self):
                        test_hooks.initialize()
                    def cleanup(self):
                        test_hooks.cleanup()
                addons = [Addon()]
            """.trimIndent()
            shouldRunLifecycleHooks(src, "addon_lifecycle.py", Language.Python, pyBuilder)
        }

        "initialize / cleanup exceptions don't crash" {
            tempfile("lifecycle.py") { file ->
                file.writeText(
                    """
                    def initialize():
                        raise Exception("oops")
                    def cleanup():
                        raise Exception("oops")
                    """.trimIndent()
                )

                Script.load(
                    UUID.randomUUID(), mockk(), file, Language.Python, mockk()
                )
            }
        }

        "req / res hook exceptions don't crash" {
            tempfile("hooks.py") { file ->
                file.writeText(
                    """
                    def on_request(req):
                        raise Exception("oops")
                    def on_response(res):
                        raise Exception("oops")
                    """.trimIndent()
                )

                val sentReq = mockk<ScriptHttpRequest>()
                val sentRes = mockk<ScriptHttpResponse>()

                // Opts that allow the script to always run, avoiding logic that would need to be mocked
                val opts = Script.Options(active = true, proxyOnly = false)
                val script = Script.load(
                    UUID.randomUUID(), mockk(), file, Language.Python, opts, pyBuilder
                )

                script.onRequest(sentReq)
                script.onResponse(sentRes)
            }
        }

        "returning nothing returns original req / res" {
            val pyNone = """
                def on_request(req):
                    return None
                def on_response(res):
                    return None
                """.trimIndent()
            shouldReturnOriginal(pyNone, "none.py", Language.Python, pyBuilder)

            val pyNoReturn = """
                def on_request(req):
                    pass
                def on_response(res):
                    pass
                """.trimIndent()
            shouldReturnOriginal(pyNoReturn, "none.py", Language.Python, pyBuilder)

            val jsNull = """
                export function onRequest(req) {
                    return null;
                }
                export function onResponse(res) {
                    return null;
                }
            """.trimIndent()
            shouldReturnOriginal(jsNull, "null.mjs", Language.JavaScript, jsBuilder)

            val jsNoReturn = """
                export function onRequest(req) {}
                export function onResponse(res) {}
            """.trimIndent()
            shouldReturnOriginal(jsNoReturn, "nothing.mjs", Language.JavaScript, jsBuilder)

            val jsUndefined = """
                export function onRequest(req) {
                    return undefined;
                }
                export function onResponse(res) {
                    return undefined;
                }
            """.trimIndent()
            shouldReturnOriginal(jsUndefined, "undefined.mjs", Language.JavaScript, jsBuilder)
        }

        "loads python modules" {
            val src = """
                def initialize():
                    test_hooks.initialize()
                def cleanup():
                    test_hooks.cleanup()
                def on_request(req):
                    return test_hooks.onRequest(req)
                def on_response(res):
                    return test_hooks.onResponse(res)
                """.trimIndent()
            shouldRunAllHooks(src, "python.py", Language.Python, pyBuilder)
        }

        "loads es6 modules" {
            val src = """
                export function initialize() {
                    testHooks.initialize();
                }
                export function cleanup() {
                    testHooks.cleanup();
                }
                export function onRequest(req) {
                    return testHooks.onRequest(req);
                }
                export function onResponse(res) {
                    return testHooks.onResponse(res);
                }
            """.trimIndent()
            shouldRunAllHooks(src, "es6.mjs", Language.JavaScript, jsBuilder)
        }

        "loads commonjs modules" {
            val src = """
                function initialize() {
                    testHooks.initialize();
                }
                function cleanup() {
                    testHooks.cleanup();
                }
                function onRequest(req) {
                    return testHooks.onRequest(req);
                }
                function onResponse(res) {
                    return testHooks.onResponse(res);
                }
                module.exports = {
                    initialize,
                    cleanup,
                    onRequest,
                    onResponse
                };
            """.trimIndent()
            shouldRunAllHooks(src, "commonjs.js", Language.JavaScript, jsBuilder)
        }

        "loads es6 addons" {
            val src = """
                class Addon {
                    initialize = function() {
                        testHooks.initialize();
                    }
                    cleanup = function() {
                        testHooks.cleanup();
                    }
                    onRequest = function(req) {
                        return testHooks.onRequest(req);
                    }
                    onResponse = function(res) {
                        return testHooks.onResponse(res);
                    }
                }
                export const addons = [new Addon()];
            """.trimIndent()
            shouldRunAllHooks(src, "es6-addon.mjs", Language.JavaScript, jsBuilder)
        }

        "loads commonjs addons" {
            val src = """
                class Addon {
                    initialize = function() {
                        testHooks.initialize();
                    }
                    cleanup = function() {
                        testHooks.cleanup();
                    }
                    onRequest = function(req) {
                        return testHooks.onRequest(req);
                    }
                    onResponse = function(res) {
                        return testHooks.onResponse(res);
                    }
                }
                module.exports = { addons: [new Addon()] };
            """.trimIndent()
            shouldRunAllHooks(src, "commonjs-addon.js", Language.JavaScript, jsBuilder)
        }
    }

    fun shouldReturnOriginal(src: String, filename: String, lang: Language, builder: ContextBuilder) {
        tempfile(filename) { file ->
            file.writeText(src)

            val sentReq = mockk<ScriptHttpRequest>()
            val sentRes = mockk<ScriptHttpResponse>()

            // Opts that allow the script to always run, avoiding logic that would need to be mocked
            val opts = Script.Options(active = true, proxyOnly = false)
            val script = Script.load(
                UUID.randomUUID(), mockk(), file, lang, opts, builder
            )

            script.onRequest(sentReq).shouldBe(sentReq)
            script.onResponse(sentRes).shouldBe(sentRes)
        }
    }

    fun shouldRunAllHooks(src: String, filename: String, lang: Language, builder: ContextBuilder) {
        tempfile(filename) { file ->
            file.writeText(src)

            val sentReq = mockk<ScriptHttpRequest>()
            val recvReq = slot<ScriptHttpRequest>()
            val sentRes = mockk<ScriptHttpResponse>()
            val recvRes = slot<ScriptHttpResponse>()

            every {
                hooks.initialize()
                hooks.cleanup()
            } just Runs
            every {
                hooks.onRequest(capture(recvReq))
            } returns sentReq
            every {
                hooks.onResponse(capture(recvRes))
            } returns sentRes

            // Opts that allow the script to always run, avoiding logic that would need to be mocked
            val opts = Script.Options(active = true, proxyOnly = false)
            val script = Script.load(
                UUID.randomUUID(), mockk(), file, lang, opts, builder
            )

            script.unload()
            script.reload()
            script.onRequest(sentReq)
            script.onResponse(sentRes)
            verify {
                hooks.initialize()
                hooks.cleanup()
                hooks.onRequest(capture(recvReq))
                hooks.onResponse(capture(recvRes))
            }

            recvReq.captured.shouldBe(sentReq)
            recvRes.captured.shouldBe(sentRes)
        }
    }

    fun shouldRunLifecycleHooks(src: String, filename: String, lang: Language, builder: ContextBuilder) {
        tempfile(filename) { file ->
            file.writeText(src)

            every {
                hooks.initialize()
                hooks.cleanup()
            } just Runs

            val script = Script.load(
                UUID.randomUUID(), mockk(), file, lang, mockk(), builder
            )

            // Load
            verify {
                hooks.initialize()
            }
            verify(exactly = 0) {
                hooks.cleanup()
            }
            script.unload()
            verify {
                hooks.cleanup()
            }

            // Reload
            clearMocks(hooks)
            script.reload()
            verify {
                hooks.initialize()
            }
            verify(exactly = 0) {
                hooks.cleanup()
            }
            script.unload()
            verify {
                hooks.cleanup()
            }
        }
    }
}


interface Hooks {
    fun initialize()
    fun cleanup()
    fun onRequest(req: ScriptHttpRequest): ScriptHttpRequest
    fun onResponse(res: ScriptHttpResponse): ScriptHttpResponse
}