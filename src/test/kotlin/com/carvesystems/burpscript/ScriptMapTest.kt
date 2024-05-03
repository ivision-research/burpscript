package com.carvesystems.burpscript

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class ScriptMapTest : StringSpec({

    "hasDotted returns true if keys exist" {
        val so = scriptMapOf(
                "level1" to mapOf("level2" to "cool")
        )
        so.hasDotted("level1.level2").shouldBeTrue()
        so.hasDotted("level1.nokey").shouldBeFalse()
        so.hasDotted("level1").shouldBeTrue()
        so.hasDotted("nokey").shouldBeFalse()
    }

    "putDotted places objects correctly" {
        val so = scriptMapOf(
                "level1" to scriptMapOf(
                        "level2" to HashMap<String, Any>()
                )
        )
        so.putDotted("level1.newkey", "value")
        so.putDotted("level1.level2.boolkey", true)
        so.putDotted("level1.level2.longkey", 12L)
        so.putDotted("level1.level2.intkey", 12)
        so.putDotted("level1.level2.realKey", 123.456)

        so.getDottedString("level1.newkey").shouldBe("value")
        so.getDottedBoolean("level1.level2.boolkey").shouldBeTrue()
        so.getDottedNumber("level1.level2.longkey").shouldBe(12L)
        so.getDottedNumber("level1.level2.intkey").shouldBe(12)
        so.getDottedNumber("level1.level2.realKey").shouldBe(123.456)
    }
})