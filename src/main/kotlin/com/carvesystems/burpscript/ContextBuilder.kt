package com.carvesystems.burpscript

import org.graalvm.polyglot.Context
import java.nio.file.Path

interface ContextBuilder {
    fun withBindings(vararg bindings: Pair<String, Any>): ContextBuilder
    fun withImportPath(path: Path): ContextBuilder {
        return this
    }

    fun withConsoleLogger(logger: ScriptLogger): ContextBuilder {
        return this
    }

    fun build(): Context

    class Default(private val language: Language) : ContextBuilder {
        private val globalBindings = mutableMapOf<String, Any>()

        override fun withBindings(vararg bindings: Pair<String, Any>): ContextBuilder {
            globalBindings.putAll(bindings.toList())
            return this
        }

        override fun build(): Context {
            val ctx = Context.newBuilder(language.id).apply {
                allowAllAccess(true)
            }.build()

            ctx.getBindings(language.id).apply {
                globalBindings.forEach { (key, value) ->
                    putMember(key, value)
                }
            }

            return ctx
        }
    }
}
