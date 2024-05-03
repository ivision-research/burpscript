package com.carvesystems.burpscript

import java.util.*

/**
 * Events for script loading status
 */
sealed class ScriptLoadEvent(val id: UUID) {

    /**
     * Published when the script successfully loads
     *
     * This is published on both initial load and reloads
     */
    class LoadSucceeded(id: UUID) : ScriptLoadEvent(id)

    /**
     * Published when a script fails to load
     */
    @Suppress("UNUSED_PARAMETER")
    class LoadFailed(id: UUID, reason: Throwable? = null) : ScriptLoadEvent(id)

}