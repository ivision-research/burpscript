package com.carvesystems.burpscript.interop


import com.carvesystems.burpscript.ScriptMap
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import org.graalvm.polyglot.Context
import javax.lang.model.type.NullType


class JsonTest : StringSpec() {
    private lateinit var ctx: Context

    init {
        beforeSpec {
            ctx = Context.newBuilder().allowAllAccess(true).build()
        }

        "Any fromJson" {
            // Parse bare primitives like json.loads and JSON.parse
            fromJson("null").shouldBeNull()
            fromJson("true").shouldBe(true)
            fromJson("false").shouldBe(false)
            fromJson("42").shouldBe(42)
            fromJson("\"hello\"").shouldBe("hello")
            fromJson("3.14").shouldBe(3.14)

            // This is unique behavior, don't throw an exception on empty values
            fromJson("").shouldBeNull()
            fromJson("\r\n").shouldBeNull()

            // Preserve numeric types, rather than converting to double
            val arr = fromJson("[1, 2.3, 4000000000000000000]") as List<*>
            arr.shouldContainExactly(listOf(1, 2.3, 4000000000000000000L))

            val arr2 = fromJson("""[{"key": "value"}]""") as List<*>
            arr2.shouldContainExactly(listOf(mapOf("key" to "value")))

            val obj = fromJson("""{"key": "value"}""") as Map<*, *>
            obj.toMap().shouldContainExactly(mapOf("key" to "value"))

            val obj2 = fromJson("""{"key": null}""") as Map<*, *>
            obj2.toMap().shouldContainExactly(mapOf("key" to null))

            // Behaviors work when nested
            val nested = fromJson("""{"key": {"nested": [1, 2.3, 4000000000000000000]}}""") as Map<*, *>
            nested.toMap().shouldContainExactly(
                mapOf("key" to mapOf("nested" to listOf(1, 2.3, 4000000000000000000L)))
            )

            // Big numbers are truncated
            fromJson("1234567890123456789012345678912345678901234567890123456789.123456789012345678901234567890123456789012345678901234567890")
                .shouldBe(1.2345678901234568E57)

            shouldThrow<SerializationException> {
                fromJson("{ \"x\" ")
            }
            shouldThrow<SerializationException> {
                fromJson("gobldegook")
            }
        }

        "fromJsonAs" {
            fromJsonAs<String>("\"hello\"").shouldBe("hello")
            fromJsonAs<Boolean>("true").shouldBe(true)
            fromJsonAs<Int>("42").shouldBe(42)
            fromJsonAs<Double>("3.14").shouldBe(3.14)

            fromJsonAs<List<Int>>("[1, 2, 3]").shouldContainExactly(listOf(1, 2, 3))

            fromJsonAs<Map<String, String>>("""{"key": "value"}""")
                .shouldContainExactly(mapOf("key" to "value"))

            fromJsonAs<Map<String, Any?>>("""{"key": "value"}""")
                .shouldContainExactly(mapOf("key" to "value"))

            fromJsonAs<Map<String, Any?>>("""{"key": null}""")
                .shouldContainExactly(mapOf("key" to null))

            fromJsonAs<List<Map<String, Any?>>>("""[{"key": "value"}]""")
                .shouldContainExactly(listOf(mapOf("key" to "value")))

            fromJsonAs<ScriptMap>("""{"key": "value"}""")
                .shouldContainExactly(mapOf("key" to "value"))
        }

        "maybeFromJsonAs" {
            maybeFromJsonAs<String>("\"hello\"").shouldNotBeNull()
            maybeFromJsonAs<Map<String, String>>("\"hello\"").shouldBeNull()
        }

        "Any toJson" {
            toJson<NullType?>(null).shouldEqualJson("null")
            toJson(true).shouldEqualJson("true")
            toJson(false).shouldEqualJson("false")
            toJson(42).shouldEqualJson("42")
            toJson(3.14).shouldEqualJson("3.14")
            toJson("hello").shouldEqualJson("\"hello\"")

            toJson(listOf(1, 2, 3)).shouldEqualJson("[1,2,3]")
            toJson(mapOf("key" to "value")).shouldEqualJson("""{"key":"value"}""")
            toJson(mapOf("key" to null)).shouldEqualJson("""{"key":null}""")
        }

        "Value toJson" {
            toJson(ctx.asValue(null)).shouldEqualJson("null")
            toJson(ctx.asValue(true)).shouldEqualJson("true")
            toJson(ctx.asValue(123)).shouldEqualJson("123")
            toJson(ctx.asValue(3.14)).shouldEqualJson("3.14")
            toJson(ctx.asValue("hello")).shouldEqualJson("\"hello\"")
            toJson(ctx.asValue(listOf(1, 2.3, 4))).shouldEqualJson("[1,2.3,4]")
            toJson(ctx.asValue(mapOf("key" to "value"))).shouldEqualJson("""{"key":"value"}""")
        }

        "fromJson makes ScriptMaps" {
            fromJson("{}").shouldBeInstanceOf<ScriptMap>()

            val arr = fromJson("[{\"key\": \"value\"}]") as List<*>
            arr[0].shouldBeInstanceOf<ScriptMap>()

            val nested = fromJson("""{"key": {"nested": "value"}}""") as ScriptMap
            nested.getDottedAs<ScriptMap>("key").getDotted("nested").shouldBe("value")
        }

        "ScriptMaps all they way down" {
            val nestedMap = fromJson("""{"key": {"nested": "value"}}""") as ScriptMap
            nestedMap.getDottedAs<ScriptMap>("key").getDotted("nested").shouldBe("value")

            // Parsing nested values behaves like fromJson(), preserving numeric types and making ScriptMaps
            val nestedNumbers = fromJson("""{"key": {"nested": [1, 2.3, 4000000000000000000]}}""") as ScriptMap
            nestedNumbers.getDottedAs<ScriptMap>("key").getDottedAs<List<*>>("nested")
                .shouldContainExactly(listOf(1, 2.3, 4000000000000000000L))

            val nestedMaps = fromJson("""{"key": {"nested": {"key": "value"}}}""") as ScriptMap
            nestedMaps.getDottedAs<ScriptMap>("key").getDottedAs<ScriptMap>("nested")
                .shouldContainExactly(mapOf("key" to "value"))
        }
    }
}

