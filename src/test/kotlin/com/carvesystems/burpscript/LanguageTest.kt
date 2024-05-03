package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.fromJsonAs
import com.carvesystems.burpscript.interop.toJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class LanguageTest : StringSpec() {
    init {
        "serialization" {
            toJson(Language.Python).shouldEqualJson("\"python\"")
            fromJsonAs<Language>("\"python\"").shouldBe(Language.Python)
        }

        "fromPath" {
            Language.fromPath(Path.of("test.py")).shouldBe(Language.Python)
            Language.fromPath(Path.of("test.js")).shouldBe(Language.JavaScript)
            Language.fromPath(Path.of("test.mjs")).shouldBe(Language.JavaScript)
        }

        "fromString" {
            Language.fromString("python").shouldBe(Language.Python)
            Language.fromString("js").shouldBe(Language.JavaScript)
            Language.fromString("mjs").shouldBe(Language.JavaScript)
        }
    }
}