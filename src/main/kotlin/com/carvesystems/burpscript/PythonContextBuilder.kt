package com.carvesystems.burpscript


import com.carvesystems.burpscript.interop.CallableValue
import kotlinx.serialization.internal.throwMissingFieldException
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class PythonContextBuilder() : ContextBuilder {
    private val logger = LogManager.getLogger(this)
    private val globalBindings = mutableMapOf<String, Any>()
    private var printLogger: ScriptLogger? = null
    private val importPaths: LinkedHashSet<Path> = LinkedHashSet()

    override fun withBindings(vararg bindings: Pair<String, Any>): ContextBuilder {
        globalBindings.putAll(bindings.toList())
        return this
    }

    override fun withConsoleLogger(logger: ScriptLogger): ContextBuilder {
        printLogger = logger
        return this
    }

    override fun withImportPath(path: Path): ContextBuilder {
        importPaths.add(path)
        return this
    }

    override fun build(): Context =
        Context.newBuilder(Language.Python.id).apply {
            allowAllAccess(true)
            updateContextBuilder(this)
        }.build().apply {
            addBindings(this.getBindings(Language.Python.id))
        }

    private fun updateContextBuilder(ctx: Context.Builder) {
        val exePath = pythonExePath()

        val paths = importPaths.toMutableList()

        var pythonPath = paths.joinToString(":")

        Config.burpScript.python?.pythonPath?.let {
            pythonPath = if (pythonPath.isNotBlank()) {
                "$pythonPath:$it"
            } else {
                it
            }
        }

        if (pythonPath.isNotEmpty()) {
            ctx.option("python.PythonPath", pythonPath)
        }

        if (exePath != null) {
            // If the host system has a python interpreter, make the embedded interpreter
            // think that it is running within the host's python environment. This doesn't
            // actually use the EXE_PATH to execute python, it only helps resolve modules.
            // https://blogs.oracle.com/javamagazine/post/java-graalvm-polyglot-python-r
            // https://docs.oracle.com/en/graalvm/jdk/20/docs/reference-manual/python/Packages/#including-packages-in-a-java-application

            ctx.option("python.ForceImportSite", "true")
            ctx.option("python.Executable", exePath)
            ctx.option("python.NativeModules", "true")
            ctx.option("python.UseSystemToolchain", "false")
        } else {
            logger.info(
                "Python interpreter was not found in PATH. You will be unable to import modules from your python environment"
            )
        }

        Config.burpScript.python?.contextOptions?.forEach {
            try {
                ctx.option(it.option, it.value)
            } catch (e: Exception) {
                logger.error(
                    "failed to set ${it.option} on context"
                )
            }
        }
    }

    private fun addBindings(bindings: Value) {
        printLogger?.let { log ->
            bindings.putMember(
                "print",
                PythonBindings.print(log)
            )
        }
        globalBindings.forEach { (n, v) -> bindings.putMember(n, v) }
    }

    // Get a path to the Python executable for locating packages. The priority is:
    //
    //  1. User's configured value
    //  2. polyglot.python.Executable property
    //  3. `python` in $PATH
    //  4. `python3` in $PATH
    //  5. Output of `which python`
    //  6. Output of `which python3`
    private fun pythonExePath(): String? =
        Config.burpScript.python?.executable
            ?: System.getProperty("polyglot.python.Executable")
            ?: pythonPathFromEnv("python")
            ?: pythonPathFromEnv("python3")
            ?: pythonPathFromWhich("python")
            ?: pythonPathFromWhich("python3")

    private fun pythonPathFromEnv(exeName: String): String? {
        return System.getenv("PATH")?.let { path ->
            val iter = path.split(':').iterator()
            while (iter.hasNext()) {
                val dir = Paths.get(iter.next())
                if (!dir.isAbsolute) {
                    continue
                }
                val python = dir.resolve(exeName)
                if (python.exists()) {
                    return python.toString()
                }
            }
            null
        }
    }

    private fun pythonPathFromWhich(exeName: String): String? {
        val proc = try {
            with(ProcessBuilder("which", exeName)) {
                redirectError(ProcessBuilder.Redirect.PIPE)
                redirectOutput(ProcessBuilder.Redirect.PIPE)
                redirectInput(ProcessBuilder.Redirect.DISCARD)
                start()
            }
        } catch (e: Exception) {
            return null
        }

        val status = proc.waitFor()
        if (status != 0) {
            return null
        }

        return proc.inputStream.readAllBytes().toString(Charset.defaultCharset()).trim()
    }
}

object PythonBindings {
    fun print(logger: ScriptLogger) =
        CallableValue<Unit> { args -> logger.info(args.first()) }
}
