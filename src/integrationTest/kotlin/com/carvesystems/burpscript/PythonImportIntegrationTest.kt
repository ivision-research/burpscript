package com.carvesystems.burpscript

import com.carvesystems.burpscript.internal.testing.tempdir
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldNotBeEmpty
import java.nio.file.Path

class PythonImportIntegrationTest : StringSpec() {
    init {
        "imports from stdlib" {
            pythonenv { env ->
                val ctx1 = env.contextBuilder.build()
                ctx1.eval("python", "import base64")
                ctx1.eval("python", "import urllib.parse")

                val ctx2 = env.contextBuilder.withImportPath(Path.of("path/to/something")).build()
                ctx2.eval("python", "import base64")
                ctx2.eval("python", "import urllib.parse")
            }
        }

        "imports from site" {
            pythonenv { env ->
                env.install(IntegrationTestEnv.resolveTestData("testpythonpkg").toString())

                val ctx = env.contextBuilder.build()
                ctx.eval("python", "import testpythonpkg")

                val ctx2 = env.contextBuilder.withImportPath(Path.of("path/to/something")).build()
                ctx2.eval("python", "import testpythonpkg")
            }
        }
    }
}

private inline fun pythonenv(block: (env: PythonEnv) -> Unit) {
    tempdir { tmp ->
        val envPath = tmp.resolve("burpscript-venv")
        block(PythonEnv(envPath))
    }
}

private class PythonEnv(val path: Path) {
    val pythonExe = path.resolve("bin/python").toString()
    val contextBuilder: PythonContextBuilder

    init {
        val res = IntegrationTestEnv.shellExec("python -m venv '$path'")
        res.ok().shouldBeTrue()

        val pythonPath = execVenv(
            "python -c \"import site; print(':'.join(site.getsitepackages()))\""
        ).trim()
        pythonPath.shouldNotBeEmpty()
        contextBuilder = PythonContextBuilder(
            PythonLangOptions(
                executable = pythonExe,
                pythonPath = pythonPath
            )
        )
    }

    fun install(vararg packages: String) {
        val pkgs = packages.joinToString(" ") { "'$it'" }
        execVenv("pip install $pkgs")
    }

    private fun execVenv(cmd: String): String {
        val activate = path.resolve("bin/activate")
        val res = IntegrationTestEnv.shellExec("source $activate && $cmd")
        res.ok().shouldBeTrue()
        return res.getStdoutString()
    }
}
