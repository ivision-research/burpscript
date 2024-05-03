package com.carvesystems.burpscript

import burp.api.montoya.core.ByteArray
import burp.api.montoya.utilities.URLUtils
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TestUrlUtils : URLUtils {
    override fun decode(string: String?): String =
        URLDecoder.decode(string, StandardCharsets.UTF_8)

    override fun decode(byteArray: ByteArray?): ByteArray {
        TODO("Not yet implemented")
    }

    override fun encode(byteArray: ByteArray?): ByteArray {
        TODO("Not yet implemented")
    }

    override fun encode(string: String?): String = URLEncoder.encode(string, StandardCharsets.UTF_8)
}