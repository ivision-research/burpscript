package com.carvesystems.burpscript

import burp.api.montoya.MontoyaApi
import burp.api.montoya.logging.Logging
import com.carvesystems.burpscript.interop.toException
import org.graalvm.polyglot.Value

class Logger(private val name: String) {
    fun info(msg: String) {
        LogManager.log("[$name] - $msg")
    }

    fun debug(msg: String) {
        if (LogManager.isDebug) {
            LogManager.log("[$name] - $msg")
        }
    }

    fun error(msg: String, e: Throwable? = null) {
        LogManager.logErr("[$name] - $msg", e)
    }

    companion object {

        fun forClass(cls: Class<*>): Logger = Logger(cls.simpleName)

    }
}

class ScriptLogger(private val name: String) {
    private var yesDebug = false

    @ScriptApi
    fun info(msg: Value) = message("INFO", msg)

    @ScriptApi
    fun debug(msg: Value) {
        if (yesDebug) {
            message("DEBUG", msg)
        }
    }

    @ScriptApi
    fun error(msg: Value, ex: Value? = null) {
        LogManager.logErr("[$name][ERROR] - $msg", ex?.toException())
    }

    @ScriptApi
    fun message(prefix: String, msg: Value) =
        LogManager.log("[$name][$prefix] - $msg")

    @ScriptApi
    fun enableDebug(enable: Boolean = true): Boolean {
        val prev = yesDebug
        yesDebug = enable
        return prev
    }
}

object LogManager {
    private var burpLogger: Logging? = null
    var isDebug = true

    fun initialize(api: MontoyaApi) {
        burpLogger = api.logging()
    }

    internal fun log(msg: String) {
        burpLogger?.logToOutput(msg)
    }

    internal fun logErr(msg: String, e: Throwable? = null) {
        burpLogger?.let {
            if (e != null) {
                it.logToError(msg, e)
            } else {
                it.logToError(msg)
            }
        }
    }

    fun getLogger(name: String): Logger = Logger(name)
    fun getLogger(inst: Any): Logger = getLogger(inst::class.java)
    fun getLogger(cls: Class<*>): Logger = Logger.forClass(cls)

    fun getScriptLogger(name: String): ScriptLogger = ScriptLogger(name)
    fun getScriptLogger(inst: Any): Logger = getScriptLogger(inst::class.java)
}