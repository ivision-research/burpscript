package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.AsStringSerializer
import kotlinx.serialization.Serializable
import org.graalvm.polyglot.Engine
import java.nio.file.Path
import kotlin.io.path.extension


@Serializable(with = LanguageSerializer::class)
sealed class Language(val id: String) {
    @Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
    @Serializable(with = LanguageSerializer::class)
    data object Python : Language("python") {
        override fun toString(): String = id
    }
    @Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
    @Serializable(with = LanguageSerializer::class)
    data object JavaScript : Language("js") {
        override fun toString(): String = id
    }
    @Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
    @Serializable(with = LanguageSerializer::class)
    class EngineSupported(id: String) : Language(id) {
        override fun toString(): String = id
    }

    companion object {
        fun fromPath(path: Path): Language =
            when (path.extension) {
                "py" -> Python
                "js", "mjs" -> JavaScript
                else -> {
                    val lang = Engine.create().languages.keys.find {
                        it == path.extension
                    } ?: throw IllegalArgumentException("Unsupported language")

                    EngineSupported(lang)
                }
            }

        fun fromString(id: String): Language =
            when (id) {
                Python.id, "py" -> Python
                JavaScript.id, "mjs" -> JavaScript
                else -> EngineSupported(id)
            }
    }
}

object LanguageSerializer : AsStringSerializer<Language>(Language::fromString)
