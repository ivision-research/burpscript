package com.carvesystems.burpscript

import java.nio.file.Path

/**
 * Events specifying what the watcher saw
 */
sealed class PathWatchEvent() {

    /**
     * Published when the [Path] has been modified
     */
    class Modified(val path: Path) : PathWatchEvent()

    /**
     * Published when the [Path] has been removed
     */
    class Removed(val path: Path) : PathWatchEvent()

}