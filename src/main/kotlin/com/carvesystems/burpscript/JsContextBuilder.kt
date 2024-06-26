package com.carvesystems.burpscript

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.file.Path

class JsContextBuilder(
    private val langOptions: JsLangOptions = Config.burpScript.js
) : ContextBuilder {
    private val globalBindings: MutableMap<String, Any> = mutableMapOf()
    private var consoleLogger: ScriptLogger? = null
    private var importPath: Path? = null

    override fun withBindings(vararg bindings: Pair<String, Any>): ContextBuilder {
        globalBindings.putAll(bindings.toList())
        return this
    }

    override fun withImportPath(path: Path): ContextBuilder {
        importPath = path
        return this
    }

    override fun withConsoleLogger(logger: ScriptLogger): ContextBuilder {
        consoleLogger = logger
        return this
    }

    override fun build(): Context =
        Context.newBuilder("js").apply {
            allowAllAccess(true)
            updateContextBuilder(this)
        }.build().apply {
            addBindings(this.getBindings("js"))
        }

    private fun updateContextBuilder(ctx: Context.Builder) {
        ctx.allowExperimentalOptions(true)

        // https://docs.oracle.com/en/graalvm/enterprise/21/docs/reference-manual/js/Modules/
        ctx.option("js.esm-eval-returns-exports", "true")
        importPath?.let { path ->
            ctx.option("js.commonjs-require", "true")
            ctx.option("js.commonjs-require-cwd", path.toString())
        }

        langOptions.contextOptions?.forEach {
            try {
                ctx.option(it.option, it.value)
            } catch (e: Exception) {
                LogManager.getLogger(this).error(
                    "failed to set ${it.option} on context"
                )
            }
        }
    }

    private fun addBindings(bindings: Value) {
        consoleLogger?.let { bindings.putMember("console", JsBindings.Console(it)) }
        globalBindings.forEach { (n, v) -> bindings.putMember(n, v) }
    }
}

object JsBindings {
    class Console(private val log: ScriptLogger) {
        @ScriptApi
        fun log(msg: Value) = log.info(msg)

        @ScriptApi
        fun warn(msg: Value) = log.message("WARN", msg)

        @ScriptApi
        fun error(msg: Value) = log.error(msg)

        @ScriptApi
        fun debug(msg: Value) = log.debug(msg)
    }
}
