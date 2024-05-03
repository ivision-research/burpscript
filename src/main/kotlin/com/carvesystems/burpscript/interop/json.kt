package com.carvesystems.burpscript.interop

import com.carvesystems.burpscript.LogManager
import com.carvesystems.burpscript.ScriptMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.graalvm.polyglot.Value
import java.nio.file.Path

/**
 * Serialize and deserialize to and from unknown types
 *
 *  - Numeric types are narrowed to int, long, or double. Arbitrary precision is not supported
 *  - Json objects are deserialized to ScriptMaps, for convenience
 *  - Serialization incurs copy overhead
 */
object AnySerializer : KSerializer<Any> {
    // Really should be a KSerializer<Any?>, but this is incompatible with registering it in
    // the serializer module.
    // https://github.com/Kotlin/kotlinx.serialization/issues/296
    //
    private val delegateSerializer = JsonElement.serializer()
    override val descriptor = delegateSerializer.descriptor
    override fun serialize(encoder: Encoder, value: Any) {
        encoder.encodeSerializableValue(delegateSerializer, value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonPrimitive = decoder.decodeSerializableValue(delegateSerializer)
        return jsonPrimitive.toAny()!! // Can actually be null
    }

    //
    // Any -> json
    //

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> toJsonObject()
        is Iterable<*> -> toJsonArray()
        else -> throw SerializationException("Unsupported type $this - ${this.javaClass}")
    }

    private fun Map<*, *>.toJsonObject(): JsonObject =
        JsonObject(this.entries.associate { it.key.toString() to it.value.toJsonElement() })

    private fun Iterable<*>.toJsonArray(): JsonArray = JsonArray(this.map { it.toJsonElement() })

    //
    // Json -> Any
    //

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonPrimitive -> toAny()
        is JsonObject -> toMap()
        is JsonArray -> toList()
    }

    private fun JsonPrimitive.toAny(): Any? = when {
        this is JsonNull -> null
        this.isString -> this.content
        else -> {
            booleanOrNull
                ?: maybeToNumber()
                // The JsonElement serializer permits bare text without signaling a syntax error.
                // A JsonLiteral is constructed, but it doesn't belong to any particular type.
                // Seems like kotlinx should treat this as a syntax error.
                ?: throw SerializationException("Unexpected JSON token at offset 0: $this")
        }
    }

    private fun JsonPrimitive.maybeToNumber(): Number? {
        intOrNull?.let { return it }
        longOrNull?.let { return it }
        doubleOrNull?.let { return it }
        return null
    }

    private fun JsonObject.toMap(): Map<String, Any?> = entries.associateTo(ScriptMap()) {
        when (val jsonElement = it.value) {
            is JsonPrimitive -> it.key to jsonElement.toAny()
            is JsonObject -> it.key to jsonElement.toMap()
            is JsonArray -> it.key to jsonElement.toList()
        }
    }

    private fun JsonArray.toList(): List<Any?> = this.map { it.toAny() }
}

object ScriptMapSerializer : KSerializer<ScriptMap> {
    private val delegateSerializer = AnySerializer
    override val descriptor = delegateSerializer.descriptor
    override fun serialize(encoder: Encoder, value: ScriptMap) {
        encoder.encodeSerializableValue(delegateSerializer, value)
    }

    override fun deserialize(decoder: Decoder): ScriptMap =
        decoder.decodeSerializableValue(delegateSerializer) as ScriptMap
}

/** Serialize a Value to json
 *
 * Deserializing a Value from json is not supported. This is because host type
 * access is not enabled by the default context (Value.asValue()).
 *
 * Host access must be enabled explicitly within a context, such as with:
 * val obj = Context.newBuilder().allowAllAccess(true).build().asValue(fromJson("{...}"))
 */
object ValueSerializer : KSerializer<Value> {
    private val delegateSerializer = JsonElement.serializer()
    override val descriptor = delegateSerializer.descriptor
    override fun serialize(encoder: Encoder, value: Value) {
        encoder.encodeSerializableValue(delegateSerializer, value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): Value =
        throw SerializationException(
            "Deserializing ${Value::class.qualifiedName} from json is not supported"
        )

    private fun Value.toJsonElement(): JsonElement =
        when {
            isNull -> JsonNull
            isBoolean -> JsonPrimitive(asBoolean())
            isNumber -> JsonPrimitive(asNumber())
            isString -> JsonPrimitive(asString())
            hasArrayElements() -> toJsonArray()
            hasHashEntries() -> toJsonObject()
            else -> throw IllegalArgumentException("Unsupported type: $this")
        }

    private fun Value.toJsonObject(): JsonObject {
        val map = mutableMapOf<String, JsonElement>()
        val keys = hashKeysIterator
        while (keys.hasIteratorNextElement()) {
            val key = keys.iteratorNextElement.toString()
            try {
                map[key] = getHashValue(key).toJsonElement()
            } catch (e: Exception) {
                val hostType = if (isHostObject) asHostObject<Any?>()?.javaClass?.simpleName else "Unknown"
                LogManager.getLogger("toMap").error(
                    "dropping value at $key because value is unexpected type $hostType - $this"
                )
            }
        }
        return JsonObject(map)
    }

    private fun Value.toJsonArray(): JsonArray {
        val lst = mutableListOf<JsonElement>()
        for (idx in 0 until arraySize) {
            try {
                lst.add(getArrayElement(idx).toJsonElement())
            } catch (e: Exception) {
                val hostType = if (isHostObject) asHostObject<Any?>()?.javaClass?.simpleName else "Unknown"
                LogManager.getLogger("toJsonArray").error(
                    "dropping value at $idx because value is unexpected type $hostType - $this"
                )
            }
        }
        return JsonArray(lst)
    }
}

/**
 * Serialize and deserialize a type as a string
 */
abstract class AsStringSerializer<T> : KSerializer<T> {
    private val fromString: (String) -> T
    private val toString: (T) -> String

    constructor(fromString: (String) -> T, toString: (T) -> String) {
        this.fromString = fromString
        this.toString = toString
    }

    constructor(fromString: (String) -> T) : this(fromString, Any?::toString as (T) -> String)

    override val descriptor =
        PrimitiveSerialDescriptor("AsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(toString(value))

    override fun deserialize(decoder: Decoder): T = fromString(decoder.decodeString())
}

/**
 * Serialize path polymorphic types as strings.
 *
 * Since path is polymorphic, it's awkward to register as contextual...
 *
 * Put this at the top of the file defining the serialized class that has Path members:
 *  @file:UseSerializers(PathSerializer::class)
 */
object PathSerializer : AsStringSerializer<Path>(Path::of)

//
// JSON utilities
//

val scriptSerializers = SerializersModule {
    contextual(ValueSerializer)
    contextual(ScriptMapSerializer)
    contextual(AnySerializer)
}

val scriptJson = Json {
    encodeDefaults = true
    serializersModule = scriptSerializers
}

/**
 * Deserialize an unknown type from JSON.
 *  - Numeric types are narrowed to int, long, or double.
 *  - Json objects are deserialized to ScriptMaps, for convenience
 */
fun fromJson(value: String, format: Json = scriptJson): Any? =
    if (value.trim().isEmpty()) {
        // json.loads and JSON.parse don't do this, but it seems better than a noisy exception
        null
    } else {
        format.decodeFromString(value)
    }

/**
 * Deserialize a known, or partially known type from JSON.
 *
 * The type can be any @Serializable, provided the serializer is registered.
 * The full type of a generic does not need to be specified:
 *  - Map<String, Any?>
 *  - List<Any?>
 */
inline fun <reified T> fromJsonAs(value: String, format: Json = scriptJson): T =
    format.decodeFromString<T>(value)

/**
 * Deserialize a known, or partially known type from JSON. Null is returned on deserialization failure.
 */
inline fun <reified T> maybeFromJsonAs(value: String, format: Json = scriptJson): T? =
    runCatching { fromJsonAs<T>(value, format) }.getOrNull()

/**
 * Serialize an object to JSON
 *
 * Can be any @Serializable or primitive type.
 */
inline fun <reified T> toJson(value: T, format: Json = scriptJson): String =
    format.encodeToString(value)

inline fun <reified T> parse(value: String): T {
    return Json {}.decodeFromString<T>(value)
}