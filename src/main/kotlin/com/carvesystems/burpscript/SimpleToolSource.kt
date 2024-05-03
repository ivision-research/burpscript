package com.carvesystems.burpscript

import burp.api.montoya.core.ToolSource
import burp.api.montoya.core.ToolType

class SimpleToolSource(private val tt: ToolType) : ToolSource {
    override fun toolType(): ToolType = tt
    override fun isFromTool(vararg toolType: ToolType): Boolean =
        toolType.any { it == tt }
}