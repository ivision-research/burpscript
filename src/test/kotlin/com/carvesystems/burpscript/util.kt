package com.carvesystems.burpscript

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively


@OptIn(ExperimentalPathApi::class)
inline fun tempdir(block: (Path) -> Unit) {
    val dir = Files.createTempDirectory("burpscript-tests").toAbsolutePath()

    try {
        block(dir)
    } finally {
        dir.deleteRecursively()
    }
}

inline fun tempfile(filename: String, block: (Path) -> Unit) {
    val fn = File(filename)
    val file = Files.createTempFile(fn.nameWithoutExtension, ".${fn.extension}").toAbsolutePath()

    try {
        block(file)
    } finally {
        file.deleteIfExists()
    }
}

object TestEnv {
    fun resolveTestData(first: String, vararg others: String): Path {
        // Assume cwd is project root
        val testData = Path.of("src", "test", "data")
        return testData.resolve(Path.of(first, *others))
    }

    fun shellExec(cmd: String): ExecResult {
        return if (System.getenv("BURPSCRIPT_NIX") != null) {
            exec("bash", "-c", cmd)
        } else {
            // Assume cwd is project root
            exec("nix", "develop", "--command", "bash", "-c", cmd)
        }
    }

    private fun exec(program: String, vararg args: String): ExecResult {
        val pb = with(ProcessBuilder(program, *args)) {
            redirectError(ProcessBuilder.Redirect.PIPE)
            redirectOutput(ProcessBuilder.Redirect.PIPE)
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

