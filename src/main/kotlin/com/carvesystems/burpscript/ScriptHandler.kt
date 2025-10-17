package com.carvesystems.burpscript

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.Registration
import burp.api.montoya.core.ToolType
import burp.api.montoya.http.handler.*
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.proxy.MessageReceivedAction
import burp.api.montoya.proxy.http.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


class ScriptHandler(
    api: MontoyaApi,
    scriptEvents: Flow.Publisher<ScriptEvent>,
    watchEvents: Flow.Publisher<PathWatchEvent>,
    private val loadEvents: SubmissionPublisher<ScriptLoadEvent>,
) : HttpHandler, ProxyRequestHandler, ProxyResponseHandler {

    private val scriptFactory = Script.Factory(api)
    private val logger = LogManager.getLogger(this)
    private val watchEventSubscriber = WatchEventSubscriber()
    private val scriptEventSubscriber = ScriptEventSubscriber()
    private val scripts: MutableList<Script> = mutableListOf()
    private val handlerRegistrations: MutableList<Registration> = mutableListOf()

    //
    //  - Synchronize modifications of the scripts array when scripts are added or removed
    //  - Synchronize the use of an underlying script context (e.g. handling proxy events) and the disposal of that
    //    context when a script is modified or removed.
    //
    private val syncScripts = ReentrantReadWriteLock()

    init {
        watchEvents.subscribe(watchEventSubscriber)
        scriptEvents.subscribe(scriptEventSubscriber)

        SaveData.forEachScript {
            logger.debug("Loading saved script ${it.id} - ${it.path}")
            loadScriptLocked(it.id, it.path, it.language, it.opts)
        }

        handlerRegistrations.add(api.http().registerHttpHandler(this))
        handlerRegistrations.add(api.proxy().registerRequestHandler(this))
        handlerRegistrations.add(api.proxy().registerResponseHandler(this))
    }

    fun close() {
        handlerRegistrations.forEach {
            it.deregister()
        }
        syncScripts.write {
            scripts.forEach {
                it.unload()
            }
        }
    }

    /** Handle requests received by the proxy */
    override fun handleRequestReceived(interceptedRequest: InterceptedRequest): ProxyRequestReceivedAction {
        val (req, action) = handleRequest(ScriptHttpRequestImpl.wrap(interceptedRequest), interceptedRequest)
        return when (action) {
            MessageReceivedAction.DROP -> ProxyRequestReceivedAction.drop()
            MessageReceivedAction.INTERCEPT -> ProxyRequestReceivedAction.intercept(req)
            else -> ProxyRequestReceivedAction.continueWith(req)
        }
    }

    /** Handle responses received by the proxy */
    override fun handleResponseReceived(interceptedResponse: InterceptedResponse): ProxyResponseReceivedAction {
        val (res, action) = handleResponse(ScriptHttpResponseImpl.wrap(interceptedResponse), interceptedResponse)
        return when (action) {
            MessageReceivedAction.DROP -> ProxyResponseReceivedAction.drop()
            MessageReceivedAction.INTERCEPT -> ProxyResponseReceivedAction.intercept(res)
            else -> ProxyResponseReceivedAction.continueWith(res)
        }
    }

    /**  invoked when an HTTP response has been processed by the Proxy before it is returned to the client.  */
    override fun handleResponseToBeSent(interceptedResponse: InterceptedResponse?): ProxyResponseToBeSentAction {
        return ProxyResponseToBeSentAction.continueWith(interceptedResponse)
    }

    /** invoked after an HTTP request has been processed by the Proxy before it is sent. */
    override fun handleRequestToBeSent(interceptedRequest: InterceptedRequest?): ProxyRequestToBeSentAction {
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest)
    }

    /** Handle requests sent from other tools */
    override fun handleHttpRequestToBeSent(requestToBeSent: HttpRequestToBeSent): RequestToBeSentAction {
        if (requestToBeSent.toolSource().isFromTool(ToolType.PROXY)) {
            // This request came from the proxy and has already been handled
            return RequestToBeSentAction.continueWith(requestToBeSent)
        }

        val (req, action) = handleRequest(ScriptHttpRequestImpl.wrap(requestToBeSent), requestToBeSent)
        if (action != MessageReceivedAction.CONTINUE) {
            logger.error(
                "Scripts can only drop or intercept requests from the proxy. Attempted $action from ${
                    requestToBeSent.toolSource().toolType()
                }. Ignoring..."
            )
        }
        return RequestToBeSentAction.continueWith(req)
    }

    /** Handle responses to requests that originate from tools other than the proxy */
    override fun handleHttpResponseReceived(responseReceived: HttpResponseReceived): ResponseReceivedAction {
        if (responseReceived.toolSource().isFromTool(ToolType.PROXY)) {
            // This request came from the proxy and has already been handled
            return ResponseReceivedAction.continueWith(responseReceived)
        }
        val (res, action) = handleResponse(ScriptHttpResponseImpl.wrap(responseReceived), responseReceived)
        if (action != MessageReceivedAction.CONTINUE) {
            logger.error(
                "Scripts can only drop or intercept responses from the proxy. Attempted $action from ${
                    responseReceived.toolSource().toolType()
                }. Ignoring.."
            )
        }
        return ResponseReceivedAction.continueWith(res)
    }

    private fun handleRequest(
        req: ScriptHttpRequest,
        original: HttpRequest
    ): Pair<HttpRequest, MessageReceivedAction> {
        var ret = req
        syncScripts.read {
            scripts.forEach {
                ret = it.onRequest(ret)
            }
        }
        if (ret.isReq(original)) {
            // Return original if the request was not modified. This prevents the thing from showing
            // up as edited in the proxy history.
            return (original to MessageReceivedAction.CONTINUE)
        }
        return (ret to ret.action())
    }

    private fun handleResponse(
        res: ScriptHttpResponse,
        original: HttpResponse
    ): Pair<HttpResponse, MessageReceivedAction> {
        var ret = res
        syncScripts.read {
            scripts.forEach {
                ret = it.onResponse(ret)
            }
        }
        if (ret.isRes(original)) {
            // Return original if the request was not modified. This prevents the thing from showing
            // up as edited in the proxy history.
            return (original to MessageReceivedAction.CONTINUE)
        }
        return (ret to ret.action())
    }

    private fun onModified(evt: PathWatchEvent.Modified) {
        val path = evt.path
        syncScripts.write {
            scripts.find { it.isPath(path) }?.let {
                try {
                    it.reload()
                    publishLoaded(it.id)
                } catch (e: java.lang.Exception) {
                    logger.error("Failed to reload script ${evt.path}", e)
                    publishLoadFailed(it.id)
                } catch (e: java.lang.Error) {
                    logger.error(
                        "Failed to reload script ${evt.path}. This is probably not recoverable. Please try restarting Burp",
                        e
                    )
                    publishLoadFailed(it.id)
                }
            }
        }
    }

    private fun publishLoaded(id: UUID) {
        val evt = ScriptLoadEvent.LoadSucceeded(id)
        loadEvents.submit(evt)
    }

    private fun publishLoadFailed(id: UUID) {
        val evt = ScriptLoadEvent.LoadFailed(id)
        loadEvents.submit(evt)
    }

    private fun onSetScript(evt: ScriptEvent.SetScript) {
        logger.debug("Setting script ${evt.id} - ${evt.path} - ${evt.language}")
        syncScripts.write {
            removeScriptLocked { it.id == evt.id }
            loadScriptLocked(evt.id, evt.path, evt.language, evt.opts)
        }
    }

    private fun loadScriptLocked(id: UUID, path: Path, language: Language, opts: Script.Options) {

        val script = try {
            scriptFactory.open(id, path, language, opts)
        } catch (e: java.lang.Exception) {
            logger.error("Failed to open script $path - $language", e)
            publishLoadFailed(id)
            return
        }

        scripts.add(script)

        try {
            script.reload()
        }catch (e: java.lang.Exception) {
            logger.error("Failed to load script $path - $language", e)
            publishLoadFailed(id)
            return
        } catch (e: java.lang.Error) {
            logger.error(
                "Failed to load script $path - ${language}. This is probably not recoverable. Please try restarting Burp",
                e
            )
            publishLoadFailed(id)
            return
        }

        publishLoaded(id)



    }

    private fun removeScriptLocked(match: (Script) -> Boolean) {
        val it = scripts.iterator()
        while (it.hasNext()) {
            val script = it.next()
            if (match(script)) {
                logger.debug("Removing $script")
                try {
                    script.unload()
                } catch (e: java.lang.Exception) {
                    logger.error("Failed to unload script $script", e)
                } catch (e: java.lang.Error) {
                    logger.error(
                        "Failed to unload script. This is probably not recoverable. Please try restarting Burp",
                        e
                    )
                }
                it.remove()
                break
            }
        }
    }

    private fun removeScript(match: (Script) -> Boolean) {
        syncScripts.write {
            removeScriptLocked(match)
        }
    }

    private fun onRemoveScript(evt: ScriptEvent.RemoveScript) {
        removeScript { it.id == evt.id }
    }

    private fun removeScript(path: Path) {
        removeScript { it.isPath(path) }
    }

    private fun onRemoved(evt: PathWatchEvent.Removed) {
        val path = evt.path
        removeScript(path)
    }

    private fun onScriptOptionsUpdated(evt: ScriptEvent.OptionsUpdated) {
        syncScripts.write {
            scripts.find {
                it.id == evt.id
            }?.setOptions(evt.opts)
        }
    }

    private inner class ScriptEventSubscriber : BaseSubscriber<ScriptEvent>() {
        override fun onNext(item: ScriptEvent?) {
            sub.request(1)
            if (item == null) {
                return
            }
            when (item) {
                is ScriptEvent.SetScript -> onSetScript(item)
                is ScriptEvent.RemoveScript -> onRemoveScript(item)
                is ScriptEvent.OptionsUpdated -> onScriptOptionsUpdated(item)
            }

        }
    }

    private inner class WatchEventSubscriber : BaseSubscriber<PathWatchEvent>() {
        override fun onNext(item: PathWatchEvent?) {
            sub.request(1)
            if (item == null) {
                return
            }
            when (item) {
                is PathWatchEvent.Modified -> onModified(item)
                is PathWatchEvent.Removed -> onRemoved(item)
            }
        }
    }
}
