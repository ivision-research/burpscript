package com.carvesystems.burpscript.ui

import burp.api.montoya.MontoyaApi
import com.carvesystems.burpscript.SaveData
import com.carvesystems.burpscript.Strings
import java.util.*
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane

class ScriptsTab(
    private val api: MontoyaApi,
    private val viewModel: ScriptsTabViewModel,
) : JPanel() {

    private val addButton = JButton(Strings.get("add"))
    private val scriptEntries = JPanel()

    private val burpUi = api.userInterface()

    init {
        setupUi()

        viewModel.setOnRemove() { onRemove(it) }
    }

    private fun setupUi() {

        SaveData.forEachScript {
            val vm = viewModel.getScriptEntryViewModel(it.id, it.opts, it.path)
            val script = ScriptEntry(api, vm)
            scriptEntries.add(script)
        }

        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        val top = JPanel()
        top.layout = BoxLayout(top, BoxLayout.X_AXIS)


        setupAddButton(top)
        add(top)

        setupScriptEntries()


        burpUi.applyThemeToComponent(this)
    }

    private fun onRemove(id: UUID) {
        scriptEntries.components.mapIndexed { i, it ->
            if (it is ScriptEntry && it.id == id) {
                i
            } else {
                -1
            }
        }.map {
            if (it != -1) {
                scriptEntries.remove(it)
            }
        }
        updateList()
    }

    private fun updateList() {
        revalidate()
        repaint()
    }

    private fun setupAddButton(parent: JPanel) {

        scriptEntries.layout = BoxLayout(scriptEntries, BoxLayout.Y_AXIS)

        addButton.addActionListener {
            val vm = viewModel.getScriptEntryViewModel()
            val script = ScriptEntry(api, vm)
            scriptEntries.add(script)
            updateList()
        }

        parent.add(addButton)

    }

    private fun setupScriptEntries() {
        val scroll = JScrollPane(scriptEntries)
        add(scroll)
    }
}