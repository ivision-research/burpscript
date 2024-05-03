package com.carvesystems.burpscript

import java.util.*

object Strings {
    private val bundle = ResourceBundle.getBundle(
        "${javaClass.packageName}.ui.strings"
    )

    fun get(key: String): String =
        bundle.getString(key)
}