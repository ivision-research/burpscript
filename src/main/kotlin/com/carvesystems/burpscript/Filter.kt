@file:Suppress("ClassName", "unused", "CanBeParameter", "MemberVisibilityCanBePrivate")

package com.carvesystems.burpscript

import burp.api.montoya.core.ToolType
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.HttpMessage
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.requests.HttpRequest
import com.carvesystems.burpscript.FilterExpressionParser.StatementContext
import com.carvesystems.burpscript.interop.maybeFromJsonAs
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.RuleNode
import java.lang.reflect.Parameter
import java.net.URI
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private val functions = listOf(
    and::class.java,
    or::class.java,
    not::class.java,
    `host-matches`::class.java,
    `file-ext-eq`::class.java,
    `path-contains`::class.java,
    `path-matches`::class.java,
    `in-scope`::class.java,
    `header-matches`::class.java,
    `has-header`::class.java,
    `has-json-key`::class.java,
    `has-cookie`::class.java,
    `has-form-param`::class.java,
    `has-query-param`::class.java,
    `body-matches`::class.java,
    `body-contains`::class.java,
    `query-param-matches`::class.java,
    `status-code-eq`::class.java,
    `status-code-in`::class.java,
    `method-eq`::class.java,
    `has-attachment`::class.java,
    `listener-port-eq`::class.java,
    `tool-source-eq`::class.java,
    `from-proxy`::class.java,
)

data class FilterFunctionDoc(
    val name: String,
    val args: List<Arg>,
    val shortDoc: String?
)

fun getFunctionDocs(): List<FilterFunctionDoc> {

    return functions.map { it ->
        val name = it.simpleName
        val doc = try {
            Strings.get("$name-doc")
        } catch (e: Exception) {
            LogManager.getLogger("getFunctionDocs").error("no doc for class ${it.simpleName}")
            null
        }
        try {
            val ann: Params = it.getAnnotation(Params::class.java)
            val args = ann.params.map { p ->
                Arg(p.name, p.type, p.isVararg)
            }

            FilterFunctionDoc(name, args, doc)
        } catch (e: Exception) {
            FilterFunctionDoc(name, listOf(), doc)
        }
    }
}

annotation class Param(
    val name: String,
    val type: ArgType,
    val isVararg: Boolean = false
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Params(
    val params: Array<Param>
)

class Arg(
    val name: String,
    val type: ArgType,
    val isVararg: Boolean = false
) {

    companion object {
        fun fromParam(param: Parameter): Arg {

            val paramAnnotation = param.getAnnotation(Param::class.java) ?: return Arg(
                if (param.isNamePresent) {
                    param.name
                } else {
                    "arg"
                },
                ArgType.Unknown
            )

            return Arg(
                paramAnnotation.name,
                paramAnnotation.type,
                isVararg = paramAnnotation.isVararg
            )
        }

        fun string(name: String): Arg = Arg(name, ArgType.String)
        fun varargString(name: String): Arg = Arg(name, ArgType.String, isVararg = true)
        fun pattern(name: String): Arg = Arg(name, ArgType.Pattern)
        fun varargPattern(name: String): Arg = Arg(name, ArgType.Pattern, isVararg = true)
        fun boolean(name: String): Arg = Arg(name, ArgType.Boolean)
        fun varargBoolean(name: String): Arg = Arg(name, ArgType.Boolean, isVararg = true)
        fun int(name: String): Arg = Arg(name, ArgType.Integer)
        fun varargInt(name: String): Arg = Arg(name, ArgType.Integer, isVararg = true)
        fun expression(name: String): Arg = Arg(name, ArgType.Expression)
        fun varargExpression(name: String): Arg = Arg(name, ArgType.Expression, isVararg = true)
    }

}

enum class ArgType {
    String,
    Pattern,
    Boolean,
    Integer,
    Expression,
    Unknown;

    override fun toString(): kotlin.String =
        when (this) {
            String -> "String"
            Pattern -> "Regex"
            Boolean -> "Boolean"
            Integer -> "Integer"
            Expression -> "Expression"
            Unknown -> "?"
        }
}

class RequestFilter(
    private val expr: FilterExpression,
) {
    fun matches(req: ScriptHttpRequest): Boolean = try {
        expr.matches(req)
    } catch (e: Exception) {
        LogManager.getLogger(this).error("expression failed with exception\nexpr = $this", e)
        false
    }

    companion object {
        fun parse(s: String): RequestFilter {
            val expr = FilterExpression.parse(s)
            return RequestFilter(expr)
        }
    }

    override fun toString(): String = expr.toString()

}

class ResponseFilter(
    private val expr: FilterExpression,
) {
    fun matches(res: ScriptHttpResponse): Boolean = try {
        expr.matches(res)
    } catch (e: Exception) {
        LogManager.getLogger(this).error("expression failed with exception\nexpr = $this", e)
        false
    }

    override fun toString(): String = expr.toString()

    companion object {
        fun parse(s: String): ResponseFilter {
            val expr = FilterExpression.parse(s)
            return ResponseFilter(expr)
        }
    }

}


private sealed class FilterArg {
    abstract val isRequest: Boolean

    class Request(val req: ScriptHttpRequest) : FilterArg() {
        override val isRequest: Boolean = true
    }

    class Response(val res: ScriptHttpResponse) : FilterArg() {
        override val isRequest: Boolean = false
    }

}

private interface IFilterStatement {
    fun matches(arg: FilterArg): Boolean
}

internal fun String.extractRaw(): String =
    slice(2 until (length - 1))

internal fun String.unquote(): String {

    val inner = this.slice(1 until (length - 1))

    val iter = inner.iterator()
    val newString = StringBuilder()
    var escaped = false
    while (iter.hasNext()) {
        val c = iter.nextChar()
        if (escaped) {
            when (c) {
                'n' -> newString.append("\n")
                't' -> newString.append("\t")
                'r' -> newString.append("\r")
                '\\' -> newString.append("\\")
                '"' -> newString.append('"')
                'x' -> {
                    val high = iter.nextChar()
                    val low = iter.nextChar()
                    val asInt = high.digitToInt(16).shl(4).and(low.digitToInt(16))
                    newString.append(asInt.toChar())
                }

                else -> throw InvalidStringEscape(this, "\\$c")
            }
            escaped = false
        } else if (c == '\\') {
            escaped = true
        } else {
            newString.append(c)
        }
    }
    return newString.toString()
}

fun FilterExpressionParser.LiteralContext.mustGetString(func: String): String =
    getString() ?: throw InvalidArg(func, this)

fun FilterExpressionParser.LiteralContext.getString(): String? {
    return STRING()?.text?.unquote() ?: RAW_STRING().text?.extractRaw()
}

fun FilterExpressionParser.LiteralContext.mustGetInt(func: String): Int {
    val asString = NUMBER()?.text ?: throw InvalidArg(func, this)

    return if (asString.startsWith("0x")) {
        asString.slice(2 until asString.length).toInt(16)
    } else if (asString.startsWith("0b")) {
        asString.slice(2 until asString.length).toInt(2)
    } else {
        asString.toInt()
    }

}

fun FilterExpressionParser.LiteralContext.mustPattern(func: String): Pattern {

    val s = mustGetString(func)
    return s.mustPattern(func)
}

fun String.mustPattern(func: String): Pattern {
    try {
        return Pattern.compile(this)
    } catch (e: PatternSyntaxException) {
        throw InvalidPattern(func, this)
    }
}

@Params(
    [
        Param("header", ArgType.String),
        Param("pattern", ArgType.Pattern)
    ]
)
private class `header-matches`(
    private val header: String,
    private val pattern: Pattern
) : StringPatternStatement() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Response -> headerMatches(arg.res)
            is FilterArg.Request -> headerMatches(arg.req)
        }

    fun headerMatches(http: HttpMessage): Boolean {
        return http.headerValue(header)?.let {
            pattern.matcher(it).matches()
        } ?: false
    }
}

private abstract class StringPatternStatement : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {

        private var str: String? = null
        private var pattern: Pattern? = null

        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            if (str == null) {
                str = mustGetString(lit)
            } else if (pattern == null) {
                pattern = mustGetPattern(lit)
            } else {
                throw TooManyArgs(funcName, 2)
            }
        }

        override fun build(): IFilterStatement {
            val s = str ?: throw MissingArg(funcName, "str", 1)
            val pat = pattern ?: throw MissingArg(funcName, "pattern", 2)
            val cons = clazz.getConstructor(String::class.java, Pattern::class.java)
            return cons.newInstance(s, pat) as IFilterStatement
        }
    }
}

private abstract class VarargStringStatement : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {
        private var strings = mutableListOf<String>()
        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            strings.add(mustGetString(lit))
        }

        override fun build(): IFilterStatement {
            if (strings.isEmpty()) {
                throw MissingArg(funcName, "str", 1)
            }
            val cons = clazz.getConstructor(List::class.java)
            return cons.newInstance(strings) as IFilterStatement
        }
    }
}

private abstract class VarargIntMatcher : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {
        private var ival = mutableListOf<Int>()
        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            ival.add(mustGetInt(lit))
        }

        override fun build(): IFilterStatement {
            if (ival.isEmpty()) {
                throw MissingArg(funcName, "int", 1)
            }
            val cons = clazz.getConstructor(List::class.java)
            return cons.newInstance(ival) as IFilterStatement
        }
    }
}

private abstract class DoubleIntMatcher : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {
        private var ival1: Int? = null
        private var ival2: Int? = null
        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            if (ival1 == null) {
                ival1 = mustGetInt(lit)
            } else if (ival2 == null) {
                ival2 = mustGetInt(lit)
            } else {
                throw TooManyArgs(funcName, 2)
            }
        }

        override fun build(): IFilterStatement {
            val i1 = ival1 ?: throw MissingArg(funcName, "int1", 1)
            val i2 = ival2 ?: throw MissingArg(funcName, "int2", 2)
            val cons = clazz.getConstructor(Int::class.java, Int::class.java)
            return cons.newInstance(i1, i2) as IFilterStatement
        }
    }
}

private abstract class NoArgStatement() : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {
        override fun build(): IFilterStatement = clazz.getConstructor().newInstance() as IFilterStatement
    }
}

private abstract class SingleIntMatcher : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {
        private var ival: Int? = null
        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            if (ival == null) {
                ival = mustGetInt(lit)
            } else {
                throw TooManyArgs(funcName, 1)
            }
        }

        override fun build(): IFilterStatement {
            val i = ival ?: throw MissingArg(funcName, "int", 1)
            val cons = clazz.getConstructor(Int::class.java)
            return cons.newInstance(i) as IFilterStatement
        }
    }
}

private abstract class SingleStringStatement : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {
        private var str: String? = null
        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            if (str == null) {
                str = mustGetString(lit)
            } else {
                throw TooManyArgs(funcName, 1)
            }
        }

        override fun build(): IFilterStatement {
            val s = str ?: throw MissingArg(funcName, "str", 1)
            val cons = clazz.getConstructor(Pattern::class.java)
            return cons.newInstance(s) as IFilterStatement
        }
    }
}

private abstract class SinglePatternMatcher : IFilterStatement {
    companion object {
        @JvmStatic
        fun getBuilder(clazz: Class<*>): IFilterFunctionBuilder = Builder(clazz)
    }

    private class Builder(private val clazz: Class<*>) : FilterFunctionBuilder(clazz.simpleName) {
        private var pattern: Pattern? = null
        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            if (pattern == null) {
                pattern = mustGetPattern(lit)
            } else {
                throw TooManyArgs(funcName, 1)
            }
        }

        override fun build(): IFilterStatement {
            val pat = pattern ?: throw MissingArg(funcName, "pattern", 1)
            val cons = clazz.getConstructor(Pattern::class.java)
            return cons.newInstance(pat) as IFilterStatement
        }
    }
}

@Params([Param("file-extensions", ArgType.String, isVararg = true)])
private class `file-ext-eq`(
    exts: List<String>
) : VarargStringStatement() {

    private val exts = exts.map {
        if (it.startsWith('.')) {
            it
        } else {
            ".$it"
        }
    }

    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> pathMatches(arg.req)
            is FilterArg.Response -> pathMatches(arg.res.initiatingRequest())
        }

    private fun pathMatches(req: HttpRequest): Boolean {
        return req.pathWithoutQuery()?.let {
            val file = it.split('/').last()
            exts.any { file.endsWith(it) }
        } ?: false
    }
}

@Params([Param("attachment-keys", ArgType.String, isVararg = true)])
private class `has-attachment`(
    private val keys: List<String>
) : VarargStringStatement() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> keys.any { arg.req.hasAttachment(it) }
            is FilterArg.Response -> keys.any { arg.res.hasAttachment(it) }
        }
}

@Params([Param("cookie", ArgType.String, isVararg = true)])
private class `has-cookie`(
    private val cookies: List<String>,
) : VarargStringStatement() {
    override fun matches(arg: FilterArg): Boolean =
        when(arg) {
            is FilterArg.Request -> cookies.any { arg.req.hasParameter(it, HttpParameterType.COOKIE) }
            is FilterArg.Response -> cookies.any { arg.res.hasCookie(it) }
        }
}

@Params([Param("headers", ArgType.String, isVararg = true)])
private class `has-header`(
    private val headers: List<String>
) : VarargStringStatement() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> headers.any { arg.req.hasHeader(it) }
            is FilterArg.Response -> headers.any { arg.res.hasHeader(it) }
        }
}

private class `from-proxy` : NoArgStatement() {
    override fun matches(arg: FilterArg): Boolean {
        val src = when (arg) {
            is FilterArg.Response -> arg.res.toolSource()
            is FilterArg.Request -> arg.req.toolSource()
        }
        return src.isFromTool(ToolType.PROXY)

    }
}

@Params([Param("tool-sources", ArgType.String, isVararg = true)])
private class `tool-source-eq`(
    sources: List<String>
) : VarargStringStatement() {

    private val sources = sources.map { it.lowercase() }

    override fun matches(arg: FilterArg): Boolean {
        val src = when (arg) {
            is FilterArg.Response -> arg.res.toolSource()
            is FilterArg.Request -> arg.req.toolSource()
        }

        val asString = src.toolType().toolName().lowercase()

        return sources.any {
            it == asString
        }
    }
}

@Params([Param("http-methods", ArgType.String, isVararg = true)])
private class `method-eq`(
    private val methods: List<String>
) : VarargStringStatement() {
    override fun matches(arg: FilterArg): Boolean {
        val req = when (arg) {
            is FilterArg.Response -> arg.res.initiatingRequest()
            is FilterArg.Request -> arg.req
        }
        return methods.contains(req.method())
    }
}

@Params([Param("path", ArgType.Pattern)])
private class `path-contains`(
    private val pattern: Pattern
) : SinglePatternMatcher() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> pathMatches(arg.req)
            is FilterArg.Response -> pathMatches(arg.res.initiatingRequest())
        }

    private fun pathMatches(req: HttpRequest): Boolean {
        return req.pathWithoutQuery()?.let {
            pattern.matcher(it).find()
        } ?: false
    }
}

@Params(
    [
        Param("param-name", ArgType.String),
        Param("value-pattern", ArgType.Pattern)
    ]
)
private class `query-param-matches`(
    private val param: String,
    private val pattern: Pattern
) :
    StringPatternStatement() {

    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> queryParamMatches(arg.req)
            is FilterArg.Response -> queryParamMatches(arg.res.initiatingRequest())
        }

    private fun queryParamMatches(req: HttpRequest): Boolean {
        val params = req.parameters()
        return params.find { it.name() == param }?.let {
            val decoded = burpUtils.urlUtils().decode(it.value())
            pattern.matcher(decoded).matches()
        } ?: false
    }
}

@Params([Param("host-pattern", ArgType.Pattern)])
private class `host-matches`(
    private val pattern: Pattern
) : SinglePatternMatcher() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> hostMatches(arg.req)
            is FilterArg.Response -> hostMatches(arg.res.initiatingRequest())
        }

    private fun hostMatches(req: HttpRequest): Boolean {
        val uri = URI.create(req.url())
        uri.host?.let {
            return pattern.matcher(it).matches()
        } ?: return false
    }
}


@Params([Param("path-pattern", ArgType.Pattern)])
private class `path-matches`(
    private val pattern: Pattern
) : SinglePatternMatcher() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> pathMatches(arg.req)
            is FilterArg.Response -> pathMatches(arg.res.initiatingRequest())
        }

    private fun pathMatches(req: HttpRequest): Boolean {
        return req.pathWithoutQuery()?.let {
            pattern.matcher(it).matches()
        } ?: false
    }
}

@Params([Param("query-params", ArgType.String, isVararg = true)])
private class `has-query-param`(
    private val params: List<String>
) : VarargStringStatement() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> hasQueryParam(arg.req)
            is FilterArg.Response -> hasQueryParam(arg.res.initiatingRequest())
        }

    private fun hasQueryParam(req: HttpRequest): Boolean =
        params.any {
            req.hasParameter(it, HttpParameterType.URL)
        }
}

@Params([Param("form-param", ArgType.String, isVararg = true)])
private class `has-form-param`(
    private val params: List<String>
) : VarargStringStatement() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> hasParam(arg.req)
            is FilterArg.Response -> hasParam(arg.res.initiatingRequest())
        }

    private fun hasParam(msg: HttpRequest): Boolean = params.any { msg.hasParameter(it, HttpParameterType.BODY) }
}

@Params([Param("json-keys", ArgType.String, isVararg = true)])
private class `has-json-key`(
    private val keys: List<String>
) : VarargStringStatement() {
    override fun matches(arg: FilterArg): Boolean {
        val obj = when (arg) {
            is FilterArg.Request -> maybeFromJsonAs<ScriptMap>(arg.req.bodyToString())
            is FilterArg.Response -> maybeFromJsonAs<ScriptMap>(arg.res.bodyToString())
        } ?: return false

        return keys.any {
            obj.hasDotted(it)
        }
    }
}

private interface IFilterFunctionBuilder {
    fun addStatement(stmt: IFilterStatement)
    fun addLiteral(lit: FilterExpressionParser.LiteralContext)

    fun build(): IFilterStatement
}

private abstract class FilterFunctionBuilder(val funcName: String) : IFilterFunctionBuilder {

    protected fun mustGetInt(lit: FilterExpressionParser.LiteralContext): Int = lit.mustGetInt(funcName)

    protected fun mustGetPattern(lit: FilterExpressionParser.LiteralContext): Pattern = lit.mustPattern(funcName)
    protected fun mustGetString(lit: FilterExpressionParser.LiteralContext): String = lit.mustGetString(funcName)
    override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
        throw InvalidArg(funcName, lit)
    }

    override fun addStatement(stmt: IFilterStatement) {
        throw InvalidArg(funcName, "nested statement")
    }
}

private class BooleanLitStatement(val value: Boolean) : IFilterStatement {
    override fun matches(arg: FilterArg): Boolean = value
}


private class `in-scope` : NoArgStatement() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> arg.req.isInScope
            is FilterArg.Response -> arg.res.initiatingRequest().isInScope
        }
}

@Params([Param("expressions-or-boolean", ArgType.Expression, true)])
private class or private constructor(
    private val statements: List<IFilterStatement>
) : IFilterStatement {

    override fun matches(arg: FilterArg): Boolean =
        statements.any { it.matches(arg) }

    companion object {
        @JvmStatic
        fun getBuilder(): FilterFunctionBuilder = Builder()
    }

    private class Builder : FilterFunctionBuilder("or") {
        private val statements = mutableListOf<IFilterStatement>()
        override fun addStatement(stmt: IFilterStatement) {
            statements.add(stmt)
        }

        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            val asBool = lit.BOOLEAN() ?: throw InvalidArg(funcName, lit.toString())
            statements.add(BooleanLitStatement(asBool.symbol.text == "true"))
        }

        override fun build(): IFilterStatement = or(statements)
    }
}

@Params([Param("expression-or-boolean", ArgType.Expression)])
private class not private constructor(
    private val statement: IFilterStatement
) : IFilterStatement {
    override fun matches(arg: FilterArg): Boolean = !statement.matches(arg)

    companion object {
        @JvmStatic
        fun getBuilder(): FilterFunctionBuilder = Builder()
    }

    private class Builder : FilterFunctionBuilder("not") {
        private var statement: IFilterStatement? = null
        override fun addStatement(stmt: IFilterStatement) {
            if (statement != null) {
                throw TooManyArgs(funcName, 1)
            }
            statement = stmt
        }

        override fun build(): IFilterStatement = not(statement ?: throw MissingArg(funcName, "statement", 1))
    }
}

@Params([Param("expressions-or-boolean", ArgType.Expression, true)])
private class and private constructor(
    private val statements: List<IFilterStatement>
) : IFilterStatement {
    override fun matches(arg: FilterArg): Boolean =
        statements.all {
            it.matches(arg)
        }

    companion object {
        @JvmStatic
        fun getBuilder(): FilterFunctionBuilder = Builder()
    }

    private class Builder : FilterFunctionBuilder("and") {
        private val statements = mutableListOf<IFilterStatement>()
        override fun addStatement(stmt: IFilterStatement) {
            statements.add(stmt)
        }

        override fun addLiteral(lit: FilterExpressionParser.LiteralContext) {
            val asBool = lit.BOOLEAN() ?: throw InvalidArg(funcName, lit.toString())
            statements.add(BooleanLitStatement(asBool.text == "true"))
        }

        override fun build(): IFilterStatement = and(statements)
    }
}

@Params(
    [
        Param("lower-bound", ArgType.Integer),
        Param("upper-bound", ArgType.Integer)
    ]
)
private class `status-code-in`(
    private val lower: Int,
    private val upper: Int
) : DoubleIntMatcher() {
    override fun matches(arg: FilterArg): Boolean {
        when (arg) {
            is FilterArg.Response -> {
                val code = arg.res.statusCode().toInt()
                return code in lower..upper
            }

            else -> throw InvalidTarget("status-code-eq", "Request")
        }
    }
}

@Params([Param("port", ArgType.Integer, isVararg = true)])
private class `listener-port-eq`(
    private val port: List<Int>
) : VarargIntMatcher() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Response -> matches(arg.res.initiatingRequest().httpService())
            is FilterArg.Request -> matches(arg.req.httpService())
        }

    private fun matches(svc: HttpService): Boolean = port.any { it.equals(svc.port()) }
}

@Params([Param("status-code", ArgType.Integer, isVararg = true)])
private class `status-code-eq`(
    private val codes: List<Int>
) : VarargIntMatcher() {
    override fun matches(arg: FilterArg): Boolean {
        when (arg) {
            is FilterArg.Response -> return codes.contains(arg.res.statusCode().toInt())
            else -> throw InvalidTarget("status-code-eq", "Request")
        }
    }
}

@Params([Param("contains-pattern", ArgType.Pattern)])
private class `body-contains`(
    private val pattern: Pattern
) : SinglePatternMatcher() {

    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> bodyMatches(arg.req)
            is FilterArg.Response -> bodyMatches(arg.res)
        }

    private fun bodyMatches(msg: HttpMessage): Boolean =
        msg.contains(pattern)
}

@Params([Param("match-pattern", ArgType.Pattern)])
private class `body-matches`(
    private val pattern: Pattern
) : SinglePatternMatcher() {
    override fun matches(arg: FilterArg): Boolean =
        when (arg) {
            is FilterArg.Request -> bodyMatches(arg.req)
            is FilterArg.Response -> bodyMatches(arg.res)
        }

    private fun bodyMatches(msg: HttpMessage): Boolean {
        val asString = msg.bodyToString()
        return pattern.matcher(asString).matches()
    }
}


class FilterExpression private constructor(
    private val raw: String,
    private val stmt: IFilterStatement
) {

    fun matches(req: ScriptHttpRequest): Boolean = stmt.matches(FilterArg.Request(req))
    fun matches(res: ScriptHttpResponse): Boolean = stmt.matches(FilterArg.Response(res))

    override fun toString(): String = raw

    companion object {
        fun parse(s: String): FilterExpression {
            val stream = CharStreams.fromString(s)
            val lexer = FilterExpressionLexer(stream)
            val tokens = CommonTokenStream(lexer)
            val parser = FilterExpressionParser(tokens)
            val tree = try {
                parser.expression()
            } catch (e: Exception) {
                throw InvalidExpression(s)
            }
            return tree.accept(ExpressionVisitor(s))
        }

    }

    private class ExpressionVisitor(private val raw: String) : FilterExpressionBaseVisitor<FilterExpression>() {
        override fun visitChildren(node: RuleNode): FilterExpression {
            val visitor = StatementVisitor()
            val stmt = node.getChild(0).accept(visitor)
            return FilterExpression(raw, stmt)
        }
    }
}


private abstract class BaseVisitor<T> : FilterExpressionBaseVisitor<T>()


private class StatementVisitor : BaseVisitor<IFilterStatement>() {

    override fun visitStatement(ctx: StatementContext): IFilterStatement {
        val funcName = ctx.FUNCNAME().text

        val funcClass = functions.find { it.simpleName == funcName } ?: throw UnknownFunction(funcName)
        val anyBuilder = try {
            val method = funcClass.getDeclaredMethod("getBuilder")
            method.invoke(null)
        } catch (e: NoSuchMethodException) {
            val sup = funcClass.superclass ?: throw RuntimeException("bad function $funcClass")
            val method = sup.getDeclaredMethod("getBuilder", Class::class.java)
            method.invoke(null, funcClass)
        }

        if (!FilterFunctionBuilder::class.java.isAssignableFrom(anyBuilder::class.java)) {
            throw RuntimeException("bad getBuilder for $funcName")
        }

        val builder = anyBuilder as FilterFunctionBuilder

        for (arg in ctx.arg()) {
            arg.statement()?.let {
                val vis = StatementVisitor()
                builder.addStatement(it.accept(vis))
            } ?: arg.literal()?.let {
                builder.addLiteral(it)
            }
        }

        return builder.build()
    }
}

sealed class FilterParseException(msg: String) : Exception(msg)

class InvalidTarget(val funcName: String, val targetName: String) :
    FilterParseException("cannot use function $funcName with target $targetName")

class InvalidExpression(val expression: String) : FilterParseException("invalid expression: $expression")

class UnknownFunction(val funcName: String) : FilterParseException("invalid function `$funcName`")

class InvalidPattern(val funcName: String, val pattern: String) :
    FilterParseException("invalid pattern in call to $funcName - $pattern")

class InvalidStringEscape(val str: String, val escape: String) :
    FilterParseException("invalid string escape `$escape` in `$str`")

class InvalidArg(val funcName: String, val argAsString: String) :
    FilterParseException("invalid argument `$argAsString` to $funcName") {
    constructor(funcName: String, lit: FilterExpressionParser.LiteralContext) : this(funcName, lit.text)
}

class TooManyArgs(val funcName: String, val expected: Int) :
    FilterParseException("too many arguments for `$funcName`, expected $expected")

class MissingArg(val funcName: String, val argName: String, val position: Int) :
    FilterParseException("function $funcName missing arg $argName at $position")
