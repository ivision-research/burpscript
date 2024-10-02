package com.carvesystems.burpscript.internal.testing.matchers.value

import com.carvesystems.burpscript.interop.asAny
import com.carvesystems.burpscript.interop.toList
import com.carvesystems.burpscript.interop.toMap
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldNotContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.graalvm.polyglot.Value


infix fun <T> Value.shouldBe(expected: T) = this.asAny() shouldBe expected
infix fun <T> Value.shouldNotBe(expected: T) = this.asAny() shouldNotBe expected

fun Value.shouldBeTrue() = this.asBoolean() shouldBe true
fun Value.shouldBeFalse() = this.asBoolean() shouldBe false

infix fun <T> Value.shouldContainExactly(expected: Iterable<T>) =
    this.toList() shouldContainExactly expected

fun <T> Value.shouldContainExactly(vararg expected: T) =
    this.toList() shouldContainExactly expected.toList()

infix fun <T> Value.shouldNotContainExactly(expected: Iterable<T>) =
    this.toList() shouldNotContainExactly expected

fun <T> Value.shouldNotContainExactly(vararg expected: T) =
    this.toList() shouldNotContainExactly expected.toList()

infix fun Value.shouldContainExactly(expected: Map<Any, Any?>) =
    this.toMap() shouldContainExactly expected

infix fun Value.shouldNotContainExactly(expected: Map<Any, Any?>) =
    this.toMap() shouldNotContainExactly expected