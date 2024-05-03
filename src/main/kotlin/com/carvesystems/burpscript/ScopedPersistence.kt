package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.*
import burp.api.montoya.persistence.PersistedObject
import burp.api.montoya.persistence.Persistence
import burp.api.montoya.persistence.Preferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Prevents key collisions for extension data and passes [Preferences] through
 * untouched
 */
class ScopedPersistence private constructor(private val wrapped: Persistence, private val namespace: String) :
    Persistence {


    override fun extensionData(): PersistedObject {
        val extData = wrapped.extensionData()

        return extData.getChildObject(namespace) ?: run {
            val newObj = PersistedObject.persistedObject()
            extData.setChildObject(namespace, newObj)
            newObj
        }
    }

    override fun preferences(): Preferences =
        wrapped.preferences()


    companion object {

        fun get(persistence: Persistence, inst: Any): ScopedPersistence = get(persistence, inst.javaClass)
        fun get(persistence: Persistence, cls: Class<*>): ScopedPersistence =
            ScopedPersistence(persistence, hash(cls.name))

        private fun hash(name: String): String =
            MessageDigest.getInstance("MD5").let {
                it.update(name.toByteArray(StandardCharsets.UTF_8))
                it.digest().toHex()
            }

    }

}