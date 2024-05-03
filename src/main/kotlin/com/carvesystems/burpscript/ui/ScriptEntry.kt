package com.carvesystems.burpscript.ui

import burp.api.montoya.MontoyaApi
import com.carvesystems.burpscript.LogManager
import com.carvesystems.burpscript.ScopedPersistence
import com.carvesystems.burpscript.Strings
import java.awt.Color
import java.awt.Dimension
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import kotlin.io.path.exists

class ScriptEntry(
    private val api: MontoyaApi,
    private val viewModel: ScriptEntryViewModel,
) : JPanel() {

    private val enabledCheckBox = JCheckBox(Strings.get("enabled"))
    private val inScopeOnlyCheckBox = JCheckBox(Strings.get("in_scope_only"))
    private val proxyOnlyCheckBox = JCheckBox(Strings.get("proxy_only"))
    private val filePath = JTextField()
    private val removeButton = JButton(Strings.get("remove"))
    private val browseButton = JButton(Strings.get("browse"))
    private val burpUi = api.userInterface()
    private val logger = LogManager.getLogger(this)
    val id = viewModel.scriptId

    private val viewModelCallbacks = Callbacks()
    private var myLastDir: String? = null

    init {
        setupUi()
        viewModel.setCallbacks(viewModelCallbacks)
    }

    private fun setupUi() {

        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        setBorder(Color.BLACK)


        val top = JPanel()
        top.layout = BoxLayout(top, BoxLayout.X_AXIS)

        val bottom = JPanel()
        bottom.layout = BoxLayout(bottom, BoxLayout.X_AXIS)

        setupEnableCheckbox(top)
        setupInScopeCheckbox(top)
        setupProxyOnlyCheckbox(top)
        setupRemoveButton(top)
        setupFileBrowsing(bottom)

        add(top)
        add(bottom)
        burpUi.applyThemeToComponent(this)
    }

    private fun setBorder(color: Color) {
        border = BorderFactory.createCompoundBorder(
            EmptyBorder(8, 8, 8, 8),
            LineBorder(color, 2, true)
        )

    }

    private fun setupProxyOnlyCheckbox(parent: JPanel) {
        proxyOnlyCheckBox.isSelected = viewModel.opts.proxyOnly

        proxyOnlyCheckBox.addChangeListener {
            val value = proxyOnlyCheckBox.isSelected
            viewModel.setProxyOnly(value)
        }

        parent.add(proxyOnlyCheckBox)
    }

    private fun setupRemoveButton(parent: JPanel) {
        removeButton.addActionListener {
            viewModel.deleted()
        }
        parent.add(removeButton)
    }

    private fun setupFileBrowsing(parent: JPanel) {
        filePath.isEditable = false
        viewModel.path?.let {
            filePath.text = it.toString()
        }
        filePath.maximumSize = Dimension(
            Int.MAX_VALUE,
            browseButton.maximumSize.height
        )
        browseButton.isEnabled = !viewModel.opts.active
        browseButton.addActionListener { onBrowse() }
        parent.add(filePath)
        parent.add(browseButton)
    }

    private fun setupInScopeCheckbox(parent: JPanel) {

        inScopeOnlyCheckBox.isSelected = viewModel.opts.inScopeOnly

        inScopeOnlyCheckBox.addChangeListener {
            val value = inScopeOnlyCheckBox.isSelected
            viewModel.setInScopeOnly(value)
        }

        parent.add(inScopeOnlyCheckBox)

    }

    private fun setupEnableCheckbox(parent: JPanel) {

        val initActive = viewModel.opts.active
        enabledCheckBox.isSelected = initActive
        enabledCheckBox.isEnabled = initActive

        enabledCheckBox.addChangeListener {
            val active = enabledCheckBox.isSelected
            viewModel.setActive(active)
            if (active) {
                disableInputItems()
            } else {
                enableInputItems()
            }
        }

        parent.add(enabledCheckBox)

    }

    private fun disableInputItems() {
        browseButton.isEnabled = false
    }

    private fun enableInputItems() {
        browseButton.isEnabled = true
    }

    private fun enableEnableCheckbox() {
        enabledCheckBox.isEnabled = true
    }

    private fun canEnable(): Boolean {
        return filePath.text.isNotEmpty()
    }

    private fun onBrowse() {
        val pers = ScopedPersistence.get(api.persistence(), this)
        val data = try {
            pers.extensionData()
        } catch (e: Exception) {
            logger.error("failed to get last dir", e)
            null
        }

        val lastDir = myLastDir ?: data?.getString(LAST_DIR_KEY) ?: System.getProperty("user.home")

        val chooser = JFileChooser(File(lastDir))
        val option = chooser.showOpenDialog(this)

        if (option != JFileChooser.APPROVE_OPTION) {
            return
        }

        val asPath = chooser.selectedFile.toPath()

        if (asPath.exists()) {
            val dir = asPath.parent.toString()
            myLastDir = dir
            data?.setString(LAST_DIR_KEY, dir)
        } else {
            return
        }

        filePath.text = asPath.toString()
        viewModel.setFile(asPath)

        if (canEnable()) {
            enableEnableCheckbox()
        }
    }

    private inner class Callbacks : ScriptEntryViewModel.Callbacks {
        override fun onLoadFailed() {
            enabledCheckBox.isSelected = false
            enabledCheckBox.isEnabled = false
            browseButton.isEnabled = true
            setBorder(Color.RED)
        }

        override fun onLoadSucceeded() {
            enabledCheckBox.isEnabled = true
            setBorder(Color.BLACK)
        }
    }

    companion object {
        private const val LAST_DIR_KEY = "last_dir"
    }

}