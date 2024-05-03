package com.carvesystems.burpscript.ui

import burp.api.montoya.MontoyaApi
import com.carvesystems.burpscript.Script
import com.carvesystems.burpscript.ScriptEvent
import com.carvesystems.burpscript.ScriptLoadEvent
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Flow
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.SubmissionPublisher

class ScriptsTabViewModel(
    private val api: MontoyaApi,
    private val scriptEventsPub: SubmissionPublisher<ScriptEvent>,
    private val loadPub: Flow.Publisher<ScriptLoadEvent>
) {

    private var onRemove: ((id: UUID) -> Unit)? = null
    private val scriptEventsSub = ScriptEventsSub()

    init {
        scriptEventsPub.subscribe(scriptEventsSub)
    }

    fun setOnRemove(onRemove: ((id: UUID) -> Unit)) {
        this.onRemove = onRemove
    }

    fun getScriptEntryViewModel(): ScriptEntryViewModel =
        ScriptEntryViewModel(loadPub, scriptEventsPub)

    fun getScriptEntryViewModel(id: UUID, opts: Script.Options, path: Path?): ScriptEntryViewModel =
        ScriptEntryViewModel(loadPub, scriptEventsPub, id, opts, path)

    private inner class ScriptEventsSub : Subscriber<ScriptEvent> {

        lateinit var sub: Flow.Subscription

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
                is ScriptEvent.RemoveScript -> {
                    onRemove?.let { it(item.id) }
                }

                else -> {}
            }

        }

        override fun onSubscribe(subscription: Flow.Subscription) {
            sub = subscription
            sub.request(1)
        }

    }

    companion object {
        private const val SCRIPTS_KEY = "scripts"
    }


}