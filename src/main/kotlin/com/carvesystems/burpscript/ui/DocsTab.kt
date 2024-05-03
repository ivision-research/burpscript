package com.carvesystems.burpscript.ui

import burp.api.montoya.MontoyaApi
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants


class DocsTab(
    private val api: MontoyaApi,
    private val viewModel: DocsTabViewModel
) : JPanel() {
    private val textDisplay = JTextPane()

    init {
        setupUi()
    }

    private fun setupUi() {

        layout = BorderLayout()

        val docs = viewModel.getFilterExpressionDocs().sortedBy {
            it.name
        }
        textDisplay.isEditable = false

        val styledDoc = textDisplay.styledDocument

        val defaultSize = textDisplay.font.size

        val funcDefStyle = SimpleAttributeSet()
        StyleConstants.setFontFamily(funcDefStyle, "monospace")
        StyleConstants.setFontSize(funcDefStyle, defaultSize + 2)

        val funcStyle = SimpleAttributeSet(funcDefStyle)
        StyleConstants.setBold(funcStyle, true)

        val argStyle = SimpleAttributeSet(funcDefStyle)

        val argNameStyle = SimpleAttributeSet(argStyle)
        StyleConstants.setForeground(argNameStyle, Color.decode("0xB16286"))
        StyleConstants.setItalic(argNameStyle, true)

        val argTypeStyle = SimpleAttributeSet(funcDefStyle)
        StyleConstants.setForeground(argTypeStyle, Color.decode("0xD65D0E"))

        val keywordStyle = SimpleAttributeSet(funcDefStyle)
        StyleConstants.setForeground(keywordStyle, Color.decode("0xD79921"))

        val docStyle = SimpleAttributeSet()

        docs.forEach {

            styledDoc.insertString(styledDoc.length, "(", funcDefStyle)
            styledDoc.insertString(styledDoc.length, it.name, funcStyle)

            it.args.forEach { arg ->

                styledDoc.insertString(styledDoc.length, " ${arg.name}", argNameStyle)
                styledDoc.insertString(styledDoc.length, ": ", funcDefStyle)

                if (arg.isVararg) {
                    styledDoc.insertString(styledDoc.length, "vararg ", keywordStyle)
                }
                styledDoc.insertString(styledDoc.length, arg.type.toString(), argTypeStyle)
            }
            styledDoc.insertString(styledDoc.length, ")\n", funcDefStyle)


            it.shortDoc?.let { shortDoc ->
                styledDoc.insertString(styledDoc.length, "$shortDoc\n\n", docStyle)
            } ?: run {
                styledDoc.insertString(styledDoc.length, "\n\n", docStyle)
            }
        }

        api.userInterface().applyThemeToComponent(textDisplay)
        api.userInterface().applyThemeToComponent(this)
        val scrollPane = JScrollPane(textDisplay)
        scrollPane.isWheelScrollingEnabled = true
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        add(scrollPane, BorderLayout.CENTER)


    }

    // Taken from StackOverflow https://stackoverflow.com/questions/30590031/jtextpane-line-wrap-behavior
    //class WrappingTextPane() : JTextPane() {
    //    init {
    //        editorKit = WrapEditorKit()
    //    }

    //    private inner class WrapEditorKit : StyledEditorKit() {
    //        private val defaultFactory: ViewFactory = WrapColumnFactory()
    //        override fun getViewFactory(): ViewFactory {
    //            return defaultFactory
    //        }
    //    }

    //    private inner class WrapColumnFactory : ViewFactory {
    //        override fun create(element: Element): View {
    //            val kind: String = element.name
    //            when (kind) {
    //                AbstractDocument.ContentElementName -> return WrapLabelView(element)
    //                AbstractDocument.ParagraphElementName -> return ParagraphView(element)
    //                AbstractDocument.SectionElementName -> return BoxView(element, View.Y_AXIS)
    //                StyleConstants.ComponentElementName -> return ComponentView(element)
    //                StyleConstants.IconElementName -> return IconView(element)
    //            }

    //            return LabelView(element)
    //        }
    //    }

    //    private inner class WrapLabelView(element: Element?) : LabelView(element) {
    //        override fun getMinimumSpan(axis: Int): Float {
    //            return when (axis) {
    //                View.X_AXIS -> 0F
    //                View.Y_AXIS -> super.getMinimumSpan(axis)
    //                else -> throw IllegalArgumentException("Invalid axis: $axis")
    //            }
    //        }
    //    }
    //}

}