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
        dottedKey.split('.').last()

    @ScriptApi
    fun hasDotted(dottedKey: String): Boolean {
        var obj: Map<String, Any?> = this
        val iter = dottedKey.split('.').iterator()
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

    private fun getParent(dottedKey: String): MutableMap<String, Any?> {
        var obj: MutableMap<String, Any?> = this
        val idx = dottedKey.lastIndexOf('.')
        if (idx == -1) {
            return obj
        }
        val parentKey = dottedKey.substring(0, idx)
        val iter = parentKey.split('.').iterator()
        while (iter.hasNext()) {
            val key = iter.next()
            try {
                @Suppress("UNCHECKED_CAST")
                obj = obj[key] as MutableMap<String, Any?>
            } catch (e: ClassCastException) {
                throw IllegalArgumentException("Cannot set value at $dottedKey")
            }
        }
        return obj
    }
}

fun scriptMapOf(vararg pairs: Pair<String, Any?>): ScriptMap =
    if (pairs.isNotEmpty()) pairs.toMap(ScriptMap()) else ScriptMap()

