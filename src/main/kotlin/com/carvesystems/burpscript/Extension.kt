package com.carvesystems.burpscript

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import com.carvesystems.burpscript.ui.DocsTab
import com.carvesystems.burpscript.ui.DocsTabViewModel
import com.carvesystems.burpscript.ui.ScriptsTab
import com.carvesystems.burpscript.ui.ScriptsTabViewModel
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import javax.swing.JTabbedPane

class Extension : BurpExtension {

    private var watchThread: WatchThread? = null

    override fun initialize(api: MontoyaApi) {
        LogManager.initialize(api)
        initializeUtils(api)

        val watchEvents = SubmissionPublisher<PathWatchEvent>()
        val scriptEvents = SubmissionPublisher<ScriptEvent>()
        val loadEvents = SubmissionPublisher<ScriptLoadEvent>()

        SaveData.initialize(api, scriptEvents)

        setupFileWatch(watchEvents, scriptEvents)
        initializeUi(api, scriptEvents, loadEvents)

        val scriptHandler = ScriptHandler(api, scriptEvents, watchEvents, loadEvents)

        val ext = api.extension()

        ext.setName(Strings.get("extension_name"))

        ext.registerUnloadingHandler {
            watchEvents.close()
            scriptEvents.close()
            watchThread?.interrupt()
            scriptHandler.close()
            watchThread = null
        }
    }

    private fun setupFileWatch(sub: SubmissionPublisher<PathWatchEvent>, pub: Flow.Publisher<ScriptEvent>) {

        watchThread?.interrupt()
        watchThread = WatchThread(sub, pub).apply { start() }
    }

    private fun initializeUi(
        api: MontoyaApi,
        pub: SubmissionPublisher<ScriptEvent>,
        loadPub: SubmissionPublisher<ScriptLoadEvent>
    ) {

        val extTab = JTabbedPane()

        val scriptsTab = ScriptsTab(api, ScriptsTabViewModel(api, pub, loadPub))
        val docsTab = DocsTab(api, DocsTabViewModel())


        extTab.addTab(Strings.get("scripts_tab_name"), scriptsTab)
        extTab.addTab(Strings.get("docs_tab_name"), docsTab)

        val ui = api.userInterface()
        ui.registerSuiteTab(
            Strings.get("tab_name"), extTab
        )
    }
}
