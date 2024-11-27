package com.carvesystems.burpscript

import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute

object IntegrationTestEnv {
    private val projectRoot: String by lazy {
        // Assume cwd is the project root, if unset. This is the case when running tests from Gradle or intellij
        System.getProperty("burpscript.testing.projectRoot", "")
    }

    fun resolveTestData(first: String, vararg others: String): Path {
        return Path.of(projectRoot, "src", "integrationTest", "data", first, *others).absolute()
    }

    fun shellExec(cmd: String): ExecResult {
        return if (System.getenv("BURPSCRIPT_NIX") != null) {
            exec("bash", "-c", cmd)
        } else {
            exec("nix", "develop", "--command", "bash", "-c", cmd)
        }
    }

    private fun exec(program: String, vararg args: String): ExecResult {
        val pb = with(ProcessBuilder(program, *args)) {
            redirectError(ProcessBuilder.Redirect.PIPE)
            redirectOutput(ProcessBuilder.Redirect.PIPE)
            if (projectRoot.isNotEmpty()) {
                directory(File(projectRoot))
            }
            start()
        }
        val input = pb.outputStream
        input.close()
        val out = pb.inputStream.readAllBytes()
        val err = pb.errorStream.readAllBytes()
        val code = pb.waitFor()

        val res = ExecResult(code, out, err)
        if (!res.ok()) {
            println("Failed to execute $program ${args.joinToString(" ")}")
            println("stdout: ${res.getStdoutString()}")
            println("stderr: ${res.getStderrString()}")
        }

        return res
    }
}
