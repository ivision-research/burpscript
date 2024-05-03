package com.carvesystems.burpscript

import burp.api.montoya.MontoyaApi
import burp.api.montoya.utilities.Utilities

lateinit var burpUtils: Utilities

fun initializeUtils(api: MontoyaApi) {
    burpUtils = api.utilities()
}