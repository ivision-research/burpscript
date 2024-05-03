package com.carvesystems.burpscript.ui

import com.carvesystems.burpscript.FilterFunctionDoc
import com.carvesystems.burpscript.getFunctionDocs

class DocsTabViewModel {

    fun getFilterExpressionDocs(): List<FilterFunctionDoc> =
        getFunctionDocs()

}