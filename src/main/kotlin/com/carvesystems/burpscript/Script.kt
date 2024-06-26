package com.carvesystems.burpscript

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ToolType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class Addon() {

    protected val logger = LogManager.getLogger(this)

    private var onReq: Value? = null
    private var onRes: Value? = null
    private var cleanup: Value? = null
    private var initialize: Value? = null

    private var reqFilter: RequestFilter? = null
    private var resFilter: ResponseFilter? = null

    fun load(value: Value) {

        val keys = value.memberKeys

        keys.forEach {
            logger.debug("Script has key: $it")
        }

        initialize = getFunction(value, "initialize", keys)
        cleanup = getFunction(value, "cleanup", keys)
        onReq = getFunction(value, listOf("on_request", "onRequest"), keys)
        onRes = getFunction(value, listOf("on_response", "onResponse"), keys)

        // These throw on syntax error. Do we want to just catch and log?
        reqFilter = getString(value, "REQ_FILTER", keys)?.let {
            RequestFilter.parse(it)
        }
        resFilter = getString(value, "RES_FILTER", keys)?.let {
            ResponseFilter.parse(it)
        }

        logger.debug("Loaded script, functions:\ninitialize = $initialize\ncleanup = $cleanup\nonReq = $onReq\nonRes = $onRes\nreqFilter = $reqFilter\nresFilter = $resFilter")

        try {
            initialize?.executeVoid()
        } catch (e: Exception) {
            logger.error("script initialize failed", e)
        }
    }

    open fun unload() {
        try {
            cleanup?.executeVoid()
        } catch (e: Exception) {
            logger.error("Failed to execute cleanup before reload", e)
        }
        onReq = null
        onRes = null
        cleanup = null
        initialize = null
        reqFilter = null
        resFilter = null
    }

    companion object {
        fun fromValue(value: Value): Addon =
            Addon().apply { load(value) }

        private fun getString(evaluated: Value, name: String, keys: Set<String>): String? {
            return keys.find {
                name == it && evaluated.getMember(it).isString
            }?.let {
                evaluated.getMember(it).asString()
            }
        }

        private fun getFunction(evaluated: Value, name: String, keys: Set<String>): Value? =
            getFunction(evaluated, listOf(name), keys)

        private fun getFunction(evaluated: Value, names: Collection<String>, keys: Set<String>): Value? =
            keys.find {
                names.contains(it) && evaluated.getMember(it).canExecute()
            }?.let {
                evaluated.getMember(it)
            }
    }

    open fun onRequest(req: ScriptHttpRequest): ScriptHttpRequest =
        onReq?.let { onRequest(it, req) } ?: req

    open fun onResponse(res: ScriptHttpResponse): ScriptHttpResponse =
        onRes?.let { onResponse(it, res) } ?: res


    private fun onRequest(cb: Value, req: ScriptHttpRequest): ScriptHttpRequest {
        if (!shouldHandle(req)) {
            return req
        }
        return try {
            val result = cb.execute(req)
            try {
                result.`as`(ScriptHttpRequest::class.java) ?: req
            } catch (e: Exception) {
                logger.error("Callback did not return an HttpRequestToBeSent", e)
                req
            }
        } catch (e: Exception) {
            logger.error("Java failed to invoke onRequest callback", e)
            req
        }

    }


    private fun onResponse(cb: Value, res: ScriptHttpResponse): ScriptHttpResponse {
        if (!shouldHandle(res)) {
            return res
        }
        return try {
            val result = cb.execute(res)
            try {
                result.`as`(ScriptHttpResponse::class.java) ?: res
            } catch (e: Exception) {
                logger.error("Callback did not return an HttpResponseReceived", e)
                res
            }
        } catch (e: Exception) {
            logger.error("Java failed to invoke onResponse callback", e)
            res
        }
    }


    private fun shouldHandle(req: ScriptHttpRequest): Boolean = reqFilter?.matches(req) ?: true
    private fun shouldHandle(res: ScriptHttpResponse): Boolean = resFilter?.matches(res) ?: true
}

class Script private constructor(
    val id: UUID,
    private val api: MontoyaApi,
    private val path: Path,
    private val language: Language,
    private var opts: Options,
    private val ctxBuilder: ContextBuilder,
) : Addon() {

    private lateinit var ctx: Context
    private val helpers = ScriptHelpers()
    private val addons = mutableListOf<Addon>()

    // JavaScript context needs a lock to prevent multi thread access
    private var lock: Lock? = if (language is Language.JavaScript) {
        ReentrantLock()
    } else {
        null
    }

    override fun onRequest(req: ScriptHttpRequest): ScriptHttpRequest {
        if (!shouldHandle(req)) {
            return req
        }
        return lock?.withLock {
            handleRequest(req)
        } ?: handleRequest(req)
    }

    private fun handleRequest(req: ScriptHttpRequest): ScriptHttpRequest {
        var newReq = super.onRequest(req)
        for (addon in addons) {
            newReq = addon.onRequest(newReq)
        }
        return newReq
    }

    override fun onResponse(res: ScriptHttpResponse): ScriptHttpResponse {
        if (!shouldHandle(res)) {
            return res
        }
        return lock?.withLock {
            handleResponse(res)
        } ?: handleResponse(res)
    }

    private fun handleResponse(res: ScriptHttpResponse): ScriptHttpResponse {
        var newRes = super.onResponse(res)
        for (addon in addons) {
            newRes = addon.onResponse(newRes)
        }
        return newRes
    }

    /**
     * Reload the script
     */
    fun reload() {
        logger.info("Loading script $path")

        if (this::ctx.isInitialized) {
            unload()
        }

        val name = path.fileName.toString()
        val scriptLogger = LogManager.getScriptLogger(name)

        ctxBuilder.apply {
            withBindings(
                // Add some globals to the script
                "api" to api,
                "log" to scriptLogger,
                "helpers" to helpers
            )
            withImportPath(path.parent)
            withConsoleLogger(scriptLogger)
        }


        try {
            // TODO Investigate sharing an engine
            ctx = ctxBuilder.build()
        } catch (e: IllegalStateException) {
            // polyglotimpl.DisableClassPathIsolation = true is the default as of 23.1.2
            // https://github.com/oracle/graal/commit/548bd7f2166254ef66ceb96a33a440101932b6d5
            throw IllegalStateException(
                "Try adding -Dpolyglotimpl.DisableClassPathIsolation=true to BurpSuite vm options and restarting. See https://portswigger.net/burp/documentation/desktop/troubleshooting/setting-java-options",
                e
            )
        }
        // Building the context can also throw an UnsatisfiedLinkError. This happens if the extension
        // was loaded multiple times. The truffle JNI shared object is implicitly loaded on first use,
        // and can only be loaded once.
        // https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.runtime/src/com/oracle/truffle/runtime/ModulesSupport.java

        val src = Source.newBuilder(language.id, path.toFile()).build()
        val evaluated = ctx.eval(src)

        load(evaluated)
        if (evaluated.hasMember("addons")) {
            loadAddons(evaluated.getMember("addons"))
        }
    }

    override fun unload() {
        logger.debug("Unloading script $path")
        super.unload()
        addons.forEach { it.unload() }
        addons.clear()

        // We may arrive here from a different thread than the one that is potentially executing
        // something within the context. Per the "Thread-Safety" section of Context, we're supposed
        // to use close(cancelIfExecuting=true).
        ctx.close(true)
    }

    private fun loadAddons(value: Value) {
        val iter = value.iterator
        while (iter.hasIteratorNextElement()) {
            val elem = iter.iteratorNextElement
            try {
                addons.add(fromValue(elem))
            } catch (e: Exception) {
                logger.error("failed to load addon", e)
            }
        }
    }

    fun setOptions(opts: Options) {
        this.opts = opts
    }


    private fun shouldHandle(req: ScriptHttpRequest): Boolean {
        if (!opts.active) {
            return false
        }
        if (opts.inScopeOnly && !req.isInScope) {
            return false
        }
        if (opts.proxyOnly && !req.toolSource().isFromTool(ToolType.PROXY)) {
            return false
        }
        return true
    }

    private fun shouldHandle(res: ScriptHttpResponse): Boolean {
        if (!opts.active) {
            return false
        }
        if (opts.inScopeOnly && !res.initiatingRequest().isInScope) {
            return false
        }
        if (opts.proxyOnly && !res.toolSource().isFromTool(ToolType.PROXY)) {
            return false
        }
        return true
    }

    fun isPath(path: Path): Boolean = path == this.path

    override fun hashCode(): Int = path.hashCode()
    override fun equals(other: Any?): Boolean = path == other


    companion object {
        /**
         * Load the given [Path] as a script of the given language.
         */
        fun load(id: UUID, api: MontoyaApi, path: Path, language: Language, opts: Options): Script {
            return load(id, api, path, language, opts, newContextBuilder(language))
        }

        /** For tests */
        fun load(
            id: UUID,
            api: MontoyaApi,
            path: Path,
            language: Language,
            opts: Options,
            builder: ContextBuilder,
        ): Script {
            return Script(id, api, path, language, opts, builder).apply { reload() }
        }

        private fun newContextBuilder(language: Language): ContextBuilder =
            when (language) {
                Language.JavaScript -> JsContextBuilder()
                Language.Python -> PythonContextBuilder()
                else -> ContextBuilder.Default(language)
            }
    }

    /**
     * All user configurable options for the script. This is connected
     * to the UI.
     */
    @Serializable()
    data class Options(
        @SerialName("a") val active: Boolean = false,
        @SerialName("i") val inScopeOnly: Boolean = false,
        @SerialName("p") val proxyOnly: Boolean = true,
    )
}

