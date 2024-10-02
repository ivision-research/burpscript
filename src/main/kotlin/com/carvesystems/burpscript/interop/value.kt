package com.carvesystems.burpscript.interop

import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable

/** Convert to the narrowest number type that can represent the value */
fun Value.asNumber(): Number = when {
    fitsInInt() -> asInt()
    fitsInLong() -> asLong()
    else -> asDouble()
}

fun Value.asAny(): Any? = when {
    isNull -> null
    isString -> asString()
    isBoolean -> asBoolean()
    isNumber -> asNumber()
    hasHashEntries() -> toMap()
    hasArrayElements() -> toList()
    isHostObject -> asHostObject()
    else -> throw IllegalArgumentException("Unsupported type: $this")
}

fun Value.toMap(): Map<Any, Any?> {
    if (!hasHashEntries()) {
        throw IllegalArgumentException("Value cannot be converted to a map: $this")
    }

    val map = mutableMapOf<Any, Any?>()
    val keys = hashKeysIterator
    while (keys.hasIteratorNextElement()) {
        val key = keys.iteratorNextElement.asAny()!!
        map[key] = getHashValue(key).asAny()
    }
    return map
}

fun Value.toList(): List<Any?> =
    if (hasArrayElements()) {
        List(arraySize.toInt()) { getArrayElement(it.toLong()).asAny() }
    } else {
        throw IllegalArgumentException("Value cannot be converted to a list: $this")
    }

fun Value.toException(): Throwable? =
    when {
        isException -> try {
            throwException()
        } catch (e: Throwable) {
            e
        }
        else -> null // Exception(toString())
    }


/**
 * Makes a free function callable from script
 */
class CallableValue<T : Any>(val func: (args: List<Value>) -> T) : ProxyExecutable {
    override fun execute(vararg arguments: Value): Any = func(arguments.toList())
}
