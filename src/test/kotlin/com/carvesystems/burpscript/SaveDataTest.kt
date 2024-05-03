package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.fromJsonAs
import com.carvesystems.burpscript.interop.toJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import java.nio.file.Path
import java.util.*

class SaveDataTest : StringSpec() {
    init {
        "serialization" {
            val path = Path.of("path")
            val id = UUID.randomUUID()
            val lang = Language.Python
            val opts = Script.Options(active = true, inScopeOnly = false, proxyOnly = true)
            val saved = SavedScript(path, id, lang, opts)

            toJson(saved).shouldEqualJson(
                """
                {
                    "m": ${id.mostSignificantBits},
                    "l": ${id.leastSignificantBits},
                    "p": "$path",
                    "g": "$lang",
                    "o": {
                        "a": true,
                        "i": false,
                        "p": true
                    }
                }
                """
            )

            val loaded = fromJsonAs<SavedScript>(
                """
                {
                    "m": ${id.mostSignificantBits},
                    "l": ${id.leastSignificantBits},
                    "p": "$path",
                    "g": "$lang",
                    "o": {
                        "a": true,
                        "i": false,
                        "p": true
                    }
                }
                """
            )
            loaded.shouldBeEqualToComparingFields(saved)
        }
    }
}