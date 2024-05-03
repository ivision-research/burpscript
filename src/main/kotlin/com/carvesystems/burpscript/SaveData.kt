@file:UseSerializers(PathSerializer::class)

package com.carvesystems.burpscript

import burp.api.montoya.MontoyaApi
import burp.api.montoya.persistence.PersistedList
import com.carvesystems.burpscript.interop.PathSerializer
import com.carvesystems.burpscript.interop.fromJsonAs
import com.carvesystems.burpscript.interop.toJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Flow
import java.util.concurrent.Flow.Publisher
import java.util.concurrent.Flow.Subscriber


object SaveData {

    private var persist: ScopedPersistence? = null
    private val scriptEventSub = ScriptEventsSub()
    private val scripts = mutableListOf<SavedScript>()
    private const val SCRIPTS_KEY = "scripts"

    fun initialize(api: MontoyaApi, scriptEvents: Publisher<ScriptEvent>) {
        if (persist == null) {
            persist = ScopedPersistence.get(api.persistence(), SaveData::class)
            loadPersisted()
            scriptEvents.subscribe(scriptEventSub)
        }
    }

    fun forEachScript(f: (SavedScript) -> Unit) {
        synchronized(scripts) {
            scripts.forEach(f)
        }
    }

    fun withScripts(f: (List<SavedScript>) -> Unit) {
        synchronized(scripts) {
            f(scripts)
        }
    }

    private fun loadPersisted() {
        val data = persist?.extensionData() ?: return
        synchronized(scripts) {
            data.getStringList(SCRIPTS_KEY)?.forEach {
                scripts.add(fromJsonAs<SavedScript>(it))
            }
        }
    }

    private fun persistScripts() {
        val data = persist?.extensionData() ?: return
        synchronized(scripts) {
            val pl = PersistedList.persistedStringList()
            pl.addAll(scripts.map {
                toJson(it)
            })
            data.setStringList(SCRIPTS_KEY, pl)
        }
    }

    private fun withScriptIdx(id: UUID, cb: (Int) -> Unit): Boolean {
        synchronized(scripts) {
            val idx = scripts.indexOfFirst { it.id == id }
            return if (idx != -1) {
                cb(idx)
                true
            } else {
                false
            }
        }
    }

    private fun onRemoved(evt: ScriptEvent.RemoveScript) {
        withScriptIdx(evt.id) {
            scripts.removeAt(it)
            persistScripts()
        }
    }

    private fun onScriptSet(evt: ScriptEvent.SetScript) {
        val had = withScriptIdx(evt.id) {
            scripts[it] = SavedScript(evt.path, evt.id, evt.language, evt.opts)
        }
        if (!had) {
            scripts.add(SavedScript(evt.path, evt.id, evt.language, evt.opts))
        }
        persistScripts()
    }

    private fun onOptionsUpdated(evt: ScriptEvent.OptionsUpdated) {
        withScriptIdx(evt.id) {
            val orig = scripts[it]
            scripts[it] = SavedScript(orig.path, evt.id, orig.language, evt.opts)
            persistScripts()
        }
    }

    private class ScriptEventsSub : Subscriber<ScriptEvent> {
        private lateinit var sub: Flow.Subscription
        override fun onComplete() {
        }

        override fun onError(throwable: Throwable?) {
        }

        override fun onNext(item: ScriptEvent?) {
            sub.request(1)
            if (item == null) {
                return
            }
            when (item) {
                is ScriptEvent.RemoveScript -> onRemoved(item)
                is ScriptEvent.SetScript -> onScriptSet(item)
                is ScriptEvent.OptionsUpdated -> onOptionsUpdated(item)
            }
        }

        override fun onSubscribe(subscription: Flow.Subscription) {
            sub = subscription
            sub.request(1)
        }
    }

}

@Serializable()
data class SavedScript(
    @SerialName("m") val idMsb: Long,
    @SerialName("l") val idLsb: Long,
    @SerialName("p") val path: Path,
    @SerialName("g") val language: Language,
    @SerialName("o") val opts: Script.Options,
) {
    @Transient
    val id = UUID(idMsb, idLsb)

    constructor(path: Path, id: UUID, language: Language, opts: Script.Options) :
            this(id.mostSignificantBits, id.leastSignificantBits, path, language, opts)

    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = id == other
}

