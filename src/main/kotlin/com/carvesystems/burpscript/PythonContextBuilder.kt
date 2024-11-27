/**
 * Import path
 *  - https://github.com/oracle/graal/issues/5043
 * GraalVM supports using _some_ python modules that rely on native C extensions,
 * but these must be built using the GraalVM python runtime (graalpy):
 *  - https://docs.oracle.com/en/graalvm/enterprise/21/docs/reference-manual/python/FAQ/#does-modulepackage-xyz-work-on-graalvms-python-runtime
 */

package com.carvesystems.burpscript


import com.carvesystems.burpscript.interop.CallableValue
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.file.Path

class PythonContextBuilder(
    private val langOptions: PythonLangOptions = Config.burpScript.python
) : ContextBuilder {
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
        var pythonPath = importPaths.joinToString(":")

        langOptions.pythonPath?.let {
            pythonPath = if (pythonPath.isNotBlank()) {
                "$pythonPath:$it"
            } else {
                it
            }
        }

        if (pythonPath.isNotBlank()) {
            ctx.option("python.PythonPath", pythonPath)
            logger.debug("Python path is: $pythonPath")
        }

        if (langOptions.executable != null) {
            // If the host system has a python interpreter, make the embedded interpreter
            // think that it is running within the host's python environment. This doesn't
            // actually use the pythonExe to execute python, it only helps resolve modules.
            // https://blogs.oracle.com/javamagazine/post/java-graalvm-polyglot-python-r
            // https://docs.oracle.com/en/graalvm/jdk/20/docs/reference-manual/python/Packages/#including-packages-in-a-java-application
            ctx.option("python.ForceImportSite", "true")
            ctx.option("python.Executable", langOptions.executable)
            ctx.option("python.NativeModules", "true")
            ctx.option("python.UseSystemToolchain", "false")
            logger.debug("Python executable is: ${langOptions.executable}")
        } else {
            logger.info(
                "Python interpreter was not found in PATH. You will be unable to import modules from your python environment"
            )
        }

        langOptions.contextOptions?.forEach {
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
}

object PythonBindings {
    fun print(logger: ScriptLogger) =
        CallableValue<Unit> { args -> logger.info(args.first()) }
}
