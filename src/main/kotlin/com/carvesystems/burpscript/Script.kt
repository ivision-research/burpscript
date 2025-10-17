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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

private class Functions(
    var onReq: Value? = null,
    var onRes: Value? = null,
    var cleanup: Value? = null,
    var initialize: Value? = null
) {

    fun clear() {
        onRes = null
        onReq = null
        initialize = null
        cleanup = null
    }


    fun reload(obj: Value) {
        val keys = obj.memberKeys
        initialize = getFunction(obj, "initialize", keys)
        cleanup = getFunction(obj, "cleanup", keys)
        onReq = getFunction(obj, listOf("on_request", "onRequest"), keys)
        onRes = getFunction(obj, listOf("on_response", "onResponse"), keys)
    }

    companion object {

        private fun getFunction(evaluated: Value, name: String, keys: Set<String>): Value? =
            getFunction(evaluated, listOf(name), keys)

        private fun getFunction(evaluated: Value, names: Collection<String>, keys: Set<String>): Value? =
            keys.find {
                names.contains(it) && evaluated.getMember(it).canExecute()
            }?.let {
                evaluated.getMember(it)
            }
    }
}

private class Addon(
    private val logger: Logger
) {

    private val functions = Functions()
    private var reqFilter: RequestFilter? = null
    private var resFilter: ResponseFilter? = null

    fun load(value: Value) {
        val keys = value.memberKeys

        //
        // Filter expressions are parsed first. Loading is stopped if these fail,
        // which ensures handlers are not called until filters are valid.
        //

        try {
            reqFilter = getString(value, "REQ_FILTER", keys)?.let {
                RequestFilter.parse(it)
            }
        } catch (e: Exception) {
            throw Exception("Error parsing REQ_FILTER", e)
        }

        try {
            resFilter = getString(value, "RES_FILTER", keys)?.let {
                ResponseFilter.parse(it)
            }
        } catch (e: Exception) {
            throw Exception("Error parsing RES_FILTER", e)
        }

        functions.reload(value)
        logger.debug("Loaded :\ninitialize = ${functions.initialize}\ncleanup = ${functions.cleanup}\nonReq = ${functions.onReq}\nonRes = ${functions.onRes}\nreqFilter = $reqFilter\nresFilter = $resFilter")

        try {
            functions.initialize?.executeVoid()
        } catch (e: Exception) {
            logger.error("script initialize failed", e)
        }
    }


    fun unload() {
        try {
            functions.cleanup?.executeVoid()
        } catch (e: Exception) {
            logger.error("Failed to execute cleanup before reload", e)
        }
        functions.clear()
        reqFilter = null
        resFilter = null
    }

    companion object {
        /**
         * Create an owned Addon from the given Value
         */
        fun fromValue(value: Value, logger: Logger): Addon =
            Addon(logger).apply { load(value) }

        private fun getString(evaluated: Value, name: String, keys: Set<String>): String? {
            return keys.find {
                name == it && evaluated.getMember(it).isString
            }?.let {
                evaluated.getMember(it).asString()
            }
        }
    }

    fun onRequest(req: ScriptHttpRequest): ScriptHttpRequest =
        if (!shouldHandle(req)) {
            req
        } else {
            functions.onReq?.let { onRequest(it, req) } ?: req
        }

    fun onResponse(res: ScriptHttpResponse): ScriptHttpResponse =
        if (!shouldHandle(res)) {
            res
        } else {
            functions.onRes?.let { onResponse(it, res) } ?: res
        }


    private fun onRequest(cb: Value, req: ScriptHttpRequest): ScriptHttpRequest {
        return try {
            val result = cb.execute(req)
            try {
                result.`as`(ScriptHttpRequest::class.java) ?: req
            } catch (e: Exception) {
                logger.error("Callback did not return a ScriptHttpRequest", e)
                req
            }
        } catch (e: Exception) {
            logger.error("Java failed to invoke onRequest callback", e)
            req
        }
    }

    private fun onResponse(cb: Value, res: ScriptHttpResponse): ScriptHttpResponse {
        return try {
            val result = cb.execute(res)
            try {
                result.`as`(ScriptHttpResponse::class.java) ?: res
            } catch (e: Exception) {
                logger.error("Callback did not return a ScriptHttpResponse", e)
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
    private val executor: ExecutorService,
    private val threadFactory: ScriptThreadFactory,
    private val logger: Logger,
) {

    private lateinit var ctx: Context
    private var base = Addon(logger)
    private val helpers = ScriptHelpers()
    private val addons = mutableListOf<Addon>()

    fun onRequest(req: ScriptHttpRequest): ScriptHttpRequest {
        if (!shouldHandle(req)) {
            return req
        }
        return onExecutor { handleRequestOnExecutor(req) }
    }

    private fun handleRequestOnExecutor(req: ScriptHttpRequest): ScriptHttpRequest {
        var newReq = base.onRequest(req)
        for (addon in addons) {
            newReq = addon.onRequest(newReq)
        }
        return newReq
    }

    fun onResponse(res: ScriptHttpResponse): ScriptHttpResponse {
        if (!shouldHandle(res)) {
            return res
        }
        return onExecutor { handleResponseOnExecutor(res) }
    }

    private fun handleResponseOnExecutor(res: ScriptHttpResponse): ScriptHttpResponse {
        var newRes = base.onResponse(res)
        for (addon in addons) {
            newRes = addon.onResponse(newRes)
        }
        return newRes
    }

    /**
     * Whether we're currently on the executor thread or not
     */
    private fun onExecutorThread(): Boolean =
        threadFactory.threadId.isPresent && threadFactory.threadId.asLong == Thread.currentThread().threadId()

    /**
     * Run the given lambda on the executor. If this addon is owned, it is assumed
     * that this function is only ever called from the executor thread
     */
    private inline fun <R> onExecutor(crossinline f: () -> R): R =
        if (onExecutorThread()) {
            f()
        } else {
            executor.submit(Callable {
                f()
            }).get()
        }


    /**
     * Reload the script
     */
    fun reload() {
        onExecutor { reloadOnExecutor() }
    }

    fun reloadOnExecutor() {
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

        base.load(evaluated)
        if (evaluated.hasMember("addons")) {
            loadAddons(evaluated.getMember("addons"))
        }
    }

    fun unload() {
        onExecutor { unloadOnExecutor() }
    }

    fun unloadOnExecutor() {
        logger.debug("Unloading script $path")
        base.unload()
        addons.forEach { it.unload() }
        addons.clear()

        // The context is only ever touched on the Executor thread so we should be able
        // to safely close it
        ctx.close()
    }

    private fun loadAddons(value: Value) {
        val iter = value.iterator
        while (iter.hasIteratorNextElement()) {
            val elem = iter.iteratorNextElement
            try {
                addons.add(Addon.fromValue(elem, logger))
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
    @Serializable
    data class Options(
        @SerialName("a") val active: Boolean = false,
        @SerialName("i") val inScopeOnly: Boolean = false,
        @SerialName("p") val proxyOnly: Boolean = true,
    )

    class Factory(private val api: MontoyaApi) {

        fun open(
            id: UUID,
            path: Path,
            language: Language,
            opts: Options,
            contextBuilder: ContextBuilder = newContextBuilder(language)
        ): Script {

            val logger = LogManager.getLogger("Script-$id")

            val threadFactory = ScriptThreadFactory(id, logger)
            val executor = Executors.newSingleThreadExecutor(threadFactory)
            val s = Script(
                id, api, path, language, opts, contextBuilder, executor, threadFactory, logger
            )
            return s
        }

        fun load(
            id: UUID,
            path: Path,
            language: Language,
            opts: Options,
            contextBuilder: ContextBuilder = newContextBuilder(language)
        ): Script {
            val script = open(id, path, language, opts, contextBuilder)
            script.reload()
            return script
        }
    }
}

class ScriptThreadFactory(private val id: UUID, private val logger: Logger) : ThreadFactory {
    private var count = 0
    private val name: String
        get() = "Script-${id}-${count}"

    var threadId: OptionalLong = OptionalLong.empty()

    override fun newThread(runnable: Runnable): Thread {
        count += 1
        val thread = Thread(runnable, name).apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                logger.error("Uncaught exception in Script thread!", e)
            }
        }
        threadId = OptionalLong.of(thread.threadId())
        return thread
    }
}

