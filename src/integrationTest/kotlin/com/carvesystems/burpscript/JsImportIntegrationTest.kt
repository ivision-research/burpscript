package com.carvesystems.burpscript

import com.carvesystems.burpscript.internal.testing.matchers.value.shouldBe
import com.carvesystems.burpscript.internal.testing.tempdir
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import org.graalvm.polyglot.Source

class JsImportIntegrationTest : StringSpec() {
    init {
        "requires from node_modules" {
            tempdir { cwd ->
                val pkg = IntegrationTestEnv.resolveTestData("testnpmpkg")

                IntegrationTestEnv.shellExec("cd '$cwd' && npm install '$pkg'")

                val ctx = JsContextBuilder().withImportPath(cwd).build()

                val import = """
                    const { doSomething } = require('testnpmpkg');
                    module.exports = { doSomething };
                """.trimIndent()
                val mod = ctx.eval(Source.newBuilder("js", import, "test.js").build())
                mod.hasMember("doSomething").shouldBeTrue()
                mod.getMember("doSomething").execute().shouldBe("did something")
            }
        }
    }
}