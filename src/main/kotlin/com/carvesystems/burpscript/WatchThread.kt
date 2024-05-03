package com.carvesystems.burpscript

import java.nio.file.*
import java.util.*
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


/**
 * Thread to watch for changes to files
 */
class WatchThread(
    private val outgoingWatchEvents: SubmissionPublisher<PathWatchEvent>,
    private val incomingFileEvents: Flow.Publisher<ScriptEvent>
) : Thread() {

    private val watch = FileSystems.getDefault().newWatchService()
    private val sub = Subscriber()
    private val watchedDirs = mutableListOf<WatchedDir>()
    private val logger = LogManager.getLogger(this)

    init {
        SaveData.forEachScript {
            addPath(it.id, it.path)
        }
    }


    override fun run() {
        incomingFileEvents.subscribe(sub)

        while (!interrupted()) {
            val key = try {
                watch.poll(250, TimeUnit.MILLISECONDS) ?: continue
            } catch (e: InterruptedException) {
                break
            }
            synchronized(watchedDirs) {
                for (evt in key.pollEvents()) {
                    handleEvent(evt)
                }
            }
            key.reset()
        }

        sub.cancel()
    }

    private fun handleEvent(evt: WatchEvent<*>) {
        when (evt.kind()) {
            StandardWatchEventKinds.ENTRY_MODIFY -> {
                onEntryModify(evt)
            }

            else -> {}
        }
    }

    private fun onEntryModify(evt: WatchEvent<*>) {
        val path = try {
            evt.context() as Path
        } catch (e: ClassCastException) {
            return
        }

        if (path.isDirectory()) {
            return
        }

        // Sometimes two modify events will be fired, one when an empty file
        // is created and one when it gets content. The downside to this check
        // is that if we ever delete the contents of the file the change is ignored
        if (path.exists() && Files.size(path) == 0L) {
            return
        }

        synchronized(watchedDirs) {
            for (wd in watchedDirs) {
                if (wd.hasFile(path)) {
                    logger.debug("Tracked file $path changed")
                    signalChanged(wd.resolve(path))
                    break
                }
            }
        }
    }


    private fun signalChanged(path: Path) {
        if (!outgoingWatchEvents.hasSubscribers()) {
            return
        }

        val evt = if (path.exists()) {
            PathWatchEvent.Modified(path)
        } else {
            PathWatchEvent.Removed(path)
        }


        outgoingWatchEvents.submit(evt)

    }

    private fun onNewPath(evt: ScriptEvent.SetScript) {
        addPath(evt.id, evt.path)
    }

    private fun addPath(id: UUID, path: Path) {
        val parent = path.parent

        synchronized(watchedDirs) {
            val wd = watchedDirs.find {
                it.isDir(parent)
            } ?: run {
                val key = parent.register(watch, StandardWatchEventKinds.ENTRY_MODIFY)
                val wd = WatchedDir(parent, key)
                watchedDirs.add(wd)
                wd
            }
            wd.add(id, path)
        }
    }

    private fun onRemovePath(evt: ScriptEvent.RemoveScript) {

        synchronized(watchedDirs) {
            val wd = watchedDirs.find {
                it.hasScript(evt.id)
            } ?: return

            if (!wd.remove(evt.id)) {
                return
            }

            if (!wd.hasFiles()) {
                wd.cancel()
                watchedDirs.remove(wd)
            }
        }
    }

    private inner class Subscriber : BaseSubscriber<ScriptEvent>() {

        override fun onNext(item: ScriptEvent?) {
            sub.request(1)
            if (item == null) {
                return
            }
            when (item) {
                is ScriptEvent.SetScript -> onNewPath(item)
                is ScriptEvent.RemoveScript -> onRemovePath(item)
                is ScriptEvent.OptionsUpdated -> {}
            }
        }
    }
}

private class WatchedScript(
    val id: UUID,
    val path: Path,
)

private class WatchedDir(
    private val dir: Path,
    private val watchKey: WatchKey,
    private val files: MutableList<WatchedScript> = mutableListOf(),
) {

    private val logger = LogManager.getLogger(this)

    fun isDir(path: Path): Boolean = dir == path
    fun hasFile(path: Path): Boolean {

        if (!files.any { it.path.fileName == path.fileName }) {
            return false
        }

        // We always watch the dir immediately containing the file, so just
        // check the parent
        if (path.isAbsolute) {
            return path.parent == dir
        }

        val resolved = dir.resolve(path)
        return resolved.exists()

    }

    fun cancel() {
        watchKey.cancel()
    }

    fun hasFiles(): Boolean = files.isNotEmpty()
    fun hasScript(id: UUID): Boolean = files.any { it.id == id }

    /**
     * Remove the given file and return whether there are still files being
     * watched
     */
    fun remove(id: UUID): Boolean =
        files.removeIf { it.id == id }

    fun add(id: UUID, file: Path) {
        files.add(WatchedScript(id, file))
    }

    fun resolve(path: Path): Path = dir.resolve(path)

}
