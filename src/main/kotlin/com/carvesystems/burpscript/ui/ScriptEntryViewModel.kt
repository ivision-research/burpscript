package com.carvesystems.burpscript.ui

import com.carvesystems.burpscript.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher

class ScriptEntryViewModel(
    private val loadEvents: Flow.Publisher<ScriptLoadEvent>,
    private val scriptEventPub: SubmissionPublisher<ScriptEvent>,
    val scriptId: UUID = UUID.randomUUID(),
    var opts: Script.Options = Script.Options(),
    var path: Path? = null,
) {

    private val loadEventsSub = LoadEventSub()

    init {
        loadEvents.subscribe(loadEventsSub)
    }

    private var callbacks: Callbacks? = null

    fun deleted() {
        val evt = ScriptEvent.RemoveScript(scriptId)
        loadEventsSub.cancel()
        sendEvent(evt)
    }

    fun setCallbacks(cb: Callbacks) {
        callbacks = cb
    }

    fun setFile(file: Path?) {

        path = file

        file?.let {
            val lang = Language.fromPath(it)
            val evt = ScriptEvent.SetScript(scriptId, it, lang, opts)
            sendEvent(evt)
        } ?: run {
            if (opts.active) {
                opts = opts.copy(active = false)
                sendOpts()
            }
        }
    }

    fun setProxyOnly(proxyOnly: Boolean) {
        opts = opts.copy(proxyOnly = proxyOnly)
        sendOpts()
    }

    fun setInScopeOnly(inScopeOnly: Boolean) {
        opts = opts.copy(inScopeOnly = inScopeOnly)
        sendOpts()
    }

    fun setActive(active: Boolean) {
        opts = opts.copy(active = active)
        sendOpts()
    }

    private fun sendOpts() {
        val evt = ScriptEvent.OptionsUpdated(scriptId, opts)
        sendEvent(evt)
    }

    private fun sendEvent(evt: ScriptEvent) {
        scriptEventPub.submit(evt)
    }

    interface Callbacks {
        fun onLoadFailed()
        fun onLoadSucceeded()
    }

    private inner class LoadEventSub : BaseSubscriber<ScriptLoadEvent>() {
        override fun onNext(item: ScriptLoadEvent?) {
            requestAnother()
            if (item == null) {
                return
            }
            when (item) {
                is ScriptLoadEvent.LoadFailed -> {
                    if (item.id == scriptId) {
                        callbacks?.onLoadFailed()
                    }

                }

                is ScriptLoadEvent.LoadSucceeded -> {
                    if (item.id == scriptId) {
                        callbacks?.onLoadSucceeded()
                    }
                }
            }
        }
    }
}