package com.carvesystems.burpscript

import com.carvesystems.burpscript.internal.testing.tempfiles
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.*
import io.kotest.matchers.collections.shouldMatchEach
import io.mockk.*
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import kotlin.io.path.writeText

class ScriptHandlerTest : StringSpec() {
    init {
        beforeSpec {
            mockkObject(SaveData)
        }

        afterTest {
            clearMocks(SaveData)
        }

        "loads saved scripts" {
            tempfiles("good.py", "bad.py") { files ->
                val (goodFile, badFile) = files

                goodFile.writeText("print('hello')")
                badFile.writeText("/")

                val goodScript = SavedScript(goodFile, UUID.randomUUID(), Language.Python, Script.Options())
                val badScript = SavedScript(badFile, UUID.randomUUID(), Language.Python, Script.Options())

                val capturedCb = slot<(SavedScript) -> Unit>()
                every {
                    SaveData.forEachScript(capture(capturedCb))
                } answers {
                    capturedCb.invoke(goodScript)
                    capturedCb.invoke(badScript)
                }

                val loadEvents = CaptureSub<ScriptLoadEvent>()
                val loadEventPub = SyncPub<ScriptLoadEvent>().apply {
                    subscribe(loadEvents)
                }

                ScriptHandler(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), loadEventPub)

                loadEvents.captured().shouldMatchEach(
                    { it shouldBe ScriptLoadEvent.LoadSucceeded(goodScript.id) },
                    { it shouldBe ScriptLoadEvent.LoadFailed(badScript.id) }
                )
            }
        }

        "loads new scripts" {
            tempfiles("good.py", "bad.py") { files ->
                val (goodFile, badFile) = files

                goodFile.writeText("print('hello')")
                badFile.writeText("/")

                val loadEvents = CaptureSub<ScriptLoadEvent>()
                val scriptEventPub = SyncPub<ScriptEvent>()
                val loadEventPub = SyncPub<ScriptLoadEvent>().apply {
                    subscribe(loadEvents)
                }

                @Suppress("UNUSED_VARIABLE")
                val handler = ScriptHandler(mockk(relaxed = true), scriptEventPub, mockk(relaxed = true), loadEventPub)

                val goodScript = ScriptEvent.SetScript(UUID.randomUUID(), goodFile, Language.Python, Script.Options())
                val badScript = ScriptEvent.SetScript(UUID.randomUUID(), badFile, Language.Python, Script.Options())

                scriptEventPub.submit(goodScript)
                scriptEventPub.submit(badScript)

                loadEvents.captured().shouldMatchEach(
                    { it shouldBe ScriptLoadEvent.LoadSucceeded(goodScript.id) },
                    { it shouldBe ScriptLoadEvent.LoadFailed(badScript.id) }
                )
            }
        }

        "reloads scripts" {
            tempfiles("saved.py", "new.py") { files ->
                val (savedFile, newFile) = files

                //
                // At first, files contain issues preventing them from loading
                //

                savedFile.writeText("/")
                newFile.writeText("/")

                val savedScript = SavedScript(savedFile, UUID.randomUUID(), Language.Python, Script.Options())
                val newScript = ScriptEvent.SetScript(UUID.randomUUID(), newFile, Language.Python, Script.Options())

                val capturedCb = slot<(SavedScript) -> Unit>()
                every {
                    SaveData.forEachScript(capture(capturedCb))
                } answers {
                    capturedCb.invoke(savedScript)
                }

                val loadEvents = CaptureSub<ScriptLoadEvent>()
                val scriptEventPub = SyncPub<ScriptEvent>()
                val watchEventPub = SyncPub<PathWatchEvent>()
                val loadEventPub = SyncPub<ScriptLoadEvent>().apply {
                    subscribe(loadEvents)
                }

                @Suppress("UNUSED_VARIABLE")
                val handler = ScriptHandler(mockk(relaxed = true), scriptEventPub, watchEventPub, loadEventPub)

                scriptEventPub.submit(newScript)

                loadEvents.captured().shouldMatchEach(
                    { it shouldBe ScriptLoadEvent.LoadFailed(savedScript.id) },
                    { it shouldBe ScriptLoadEvent.LoadFailed(newScript.id) }
                )

                //
                // But then, they are modified and can be reloaded
                //

                savedFile.writeText("print('Yes')")
                newFile.writeText("print('Good')")

                watchEventPub.submit(PathWatchEvent.Modified(savedFile))
                watchEventPub.submit(PathWatchEvent.Modified(newFile))

                loadEvents.captured().shouldMatchEach(
                    { it shouldBe ScriptLoadEvent.LoadSucceeded(savedScript.id) },
                    { it shouldBe ScriptLoadEvent.LoadSucceeded(newScript.id) }
                )
            }
        }
    }

    private inner class SyncPub<T> : SubmissionPublisher<T>(
        Executor { command -> command.run() },
        Flow.defaultBufferSize()
    )

    private inner class CaptureSub<T> : BaseSubscriber<T>() {
        private var events = mutableListOf<T>()

        fun captured(): List<T> {
            val res = events
            events = mutableListOf()
            return res
        }

        override fun onNext(item: T?) {
            requestAnother()
            if (item == null) {
                return
            }
            events.add(item)
        }
    }
}

private infix fun ScriptLoadEvent.shouldBe(other: ScriptLoadEvent) {
    this.id shouldBe other.id
    this::class shouldBe other::class
}
