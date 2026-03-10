package com.carvesystems.burpscript


class ScriptMap : HashMap<String, Any?> {
    constructor() : super()

    constructor(map: Map<String, Any?>) : super(map)

    @ScriptApi
    fun getDotted(key: String): Any? =
        getParent(key)[finalKey(key)]

    @ScriptApi
    fun getDottedString(key: String): String =
        getDottedAs(key)

    @ScriptApi
    fun getDottedBoolean(key: String): Boolean =
        getDottedAs(key)

    @ScriptApi
    fun getDottedNumber(key: String): Number =
        getDottedAs(key)


    @ScriptApi
    fun put(key: String, value: Iterable<*>?) {
        put(key, value?.toList())
    }

    @ScriptApi
    fun put(key: String, value: Iterator<*>?) {
        put(key, value?.asSequence()?.toList())
    }

    @ScriptApi
    fun putDotted(key: String, value: Iterable<*>?) {
        getParent(key).put(finalKey(key), value)
    }

    @ScriptApi
    fun putDotted(key: String, value: Iterator<*>?) {
        getParent(key).put(finalKey(key), value)
    }

    @ScriptApi
    fun putDotted(key: String, value: String?) {
        getParent(key).put(finalKey(key), value)
    }

    @ScriptApi
    fun putDotted(key: String, value: Number?) {
        getParent(key).put(finalKey(key), value)
    }

    @ScriptApi
    fun putDotted(key: String, value: Boolean?) {
        getParent(key).put(finalKey(key), value)
    }

    @ScriptApi
    fun putDotted(key: String, value: Map<*, *>?) {
        getParent(key).put(finalKey(key), value)
    }

    @ScriptApi
    fun putDotted(key: String, value: Collection<*>?) {
        getParent(key).put(finalKey(key), value)
    }

    fun <T> getDottedAs(key: String): T =
        @Suppress("UNCHECKED_CAST")
        (getDotted(key) as T)

    fun <T> getAs(key: String): T? =
        try {
            @Suppress("UNCHECKED_CAST")
            get(key) as T
        } catch (e: Exception) {
            null
        }

    fun <T> maybeGetDottedAs(key: String): T? =
        try {
            getDottedAs(key)
        } catch (e: Exception) {
            null
        }

    private fun finalKey(dottedKey: String): String =
        SimpleEscapeSplitter('.').split(dottedKey).last()

    @ScriptApi
    fun hasDotted(dottedKey: String): Boolean {
        var obj: Map<String, Any?> = this
        val iter = SimpleEscapeSplitter('.').split(dottedKey).iterator()
        while (iter.hasNext()) {
            val key = iter.next()
            if (!iter.hasNext()) {
                return obj.containsKey(key)
            }
            obj = try {
                @Suppress("UNCHECKED_CAST")
                obj[key] as Map<String, Any?>
            } catch (e: ClassCastException) {
                return false
            }
        }
        return false
    }

    /**
     * Return the parent of a given dotted key, for example: "foo.bar.baz" on JSON
     * of the form:
     *
     * {
     *      "foo": {
     *          "bar": {
     *              "baz": 12
     *          }
     *      }
     * }
     *
     * would return the object: obj["foo"]["bar"]
     */
    private fun getParent(dottedKey: String): MutableMap<String, Any?> {
        var obj: MutableMap<String, Any?> = this
        val iter = SimpleEscapeSplitter('.').split(dottedKey).iterator()
        // If there weren't any dots, return the object itself
        if (!iter.hasNext()) {
            return obj
        }

        while (iter.hasNext()) {
            val key = iter.next()
            // If there is no next, this was the last key so we just return
            // the object
            if (!iter.hasNext()) {
                return obj
            }

            // Otherwise try to get the inner object
            try {
                @Suppress("UNCHECKED_CAST")
                obj = obj[key] as MutableMap<String, Any?>
            } catch (e: ClassCastException) {
                throw IllegalArgumentException("Cannot set value at $dottedKey")
            }
        }
        // This is unreachable
        return obj
    }
}

fun scriptMapOf(vararg pairs: Pair<String, Any?>): ScriptMap =
    if (pairs.isNotEmpty()) pairs.toMap(ScriptMap()) else ScriptMap()


/**
 * Splits a string on a value that may be escaped.
 *
 * For example:
 *
 *  SimpleEscapeSplitter(',').split("foo,bar\\n\\,baz,bar")
 *
 * Would create a sequence returning:
 *  - "foo"
 *  - "bar\\n,baz"
 *  - "bar"
 */
class SimpleEscapeSplitter(private val splitChar: Char) {
    fun split(s: String): Sequence<String> {

        val chars = s.toCharArray()
        val count = chars.size
        var idx = 0
        val part = StringBuilder()
        var escaped = false
        return generateSequence {
            if (idx >= count) {
                return@generateSequence null
            }
            while (idx < count) {
                val c = chars[idx]
                idx += 1
                if (escaped) {
                    // Restore the escape
                    if (c != splitChar) {
                        part.append('\\')
                    }
                    part.append(c)
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == splitChar) {
                    val string = part.toString()
                    part.clear()
                    return@generateSequence string
                } else {
                    part.append(c)
                }
            }
            val string = part.toString()
            part.clear()
            return@generateSequence string
        }
    }
}
