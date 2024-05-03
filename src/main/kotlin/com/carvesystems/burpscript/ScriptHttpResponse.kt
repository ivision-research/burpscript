package com.carvesystems.burpscript

import burp.api.montoya.core.*
import burp.api.montoya.core.ByteArray
import burp.api.montoya.http.handler.HttpResponseReceived
import burp.api.montoya.http.message.Cookie
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.MimeType
import burp.api.montoya.http.message.StatusCodeClass
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.http.message.responses.analysis.Attribute
import burp.api.montoya.http.message.responses.analysis.AttributeType
import burp.api.montoya.http.message.responses.analysis.KeywordCount
import burp.api.montoya.proxy.MessageReceivedAction
import burp.api.montoya.proxy.http.InterceptedResponse
import com.carvesystems.burpscript.interop.fromJson
import com.carvesystems.burpscript.interop.toBurpByteArray
import com.carvesystems.burpscript.interop.toJson
import org.graalvm.polyglot.Value
import java.util.regex.Pattern

/**
 * Extend the [HttpResponseReceived] interface to provide some extra
 * functionality to scripts and work around some issues with the Burp provided
 * object.
 */
interface ScriptHttpResponse : HttpResponseReceived {
    /** True if the response contains an attachment with a certain file name */
    fun hasAttachment(key: String): Boolean

    /** Gets an attachment, if it exists, as a string */
    fun getAttachment(key: String): String?

    /** Add an attachment to the response */
    fun withAttachment(key: String, data: String): ScriptHttpResponse

    /**
     * Parse body as JSON
     * ScriptMap | List | String | Number | Boolean | null
     */
    fun bodyToJson(): Any?

    /**
     * Serialize `value` to JSON and set the body
     */
    fun withJson(value: Value): HttpResponse

    /**
     * Set the body to signed or unsigned bytes
     */
    fun withBytes(signedOrUnsignedBytes: Value): HttpResponse

    /**
     * Drop this response, do not send this response to the client.
     * Not that this is only applicable if the script is handling a response from the proxy.
     * i.e. the script has been configured as "Proxy Only", or resp.toolSource() == ToolType.PROXY
     */
    fun drop(): ScriptHttpResponse

    /**
     * Cause the proxy to present this response for review and editing in the Intercept tab
     * Not that this is only applicable if the script is handling a response from the proxy.
     * i.e. the script has been configured as "Proxy Only", or resp.toolSource() == ToolType.PROXY
     */
    fun intercept(): ScriptHttpResponse

    fun action(): MessageReceivedAction

    @Internal
    fun isRes(res: HttpResponse): Boolean
}


class ScriptHttpResponseImpl(
    private val res: HttpResponse,
    private val req: HttpRequest,
    private val annotations: Annotations,
    private val toolSource: ToolSource,
    private val messageId: Int,
    private val attachments: MutableMap<String, String>,
    private val action: MessageReceivedAction,
) : ScriptHttpResponse {

    companion object {

        fun wrap(res: InterceptedResponse): ScriptHttpResponse =
            ScriptHttpResponseImpl(
                res,
                res.initiatingRequest(),
                // This is a reference, we want to be able to modify the annotations of the original res
                res.annotations(),
                SimpleToolSource(ToolType.PROXY),
                res.messageId(),
                mutableMapOf(),
                MessageReceivedAction.CONTINUE
            )

        fun wrap(res: HttpResponseReceived): ScriptHttpResponse =
            ScriptHttpResponseImpl(
                res,
                res.initiatingRequest(),
                // This is a reference, we want to be able to modify the annotations of the original res
                res.annotations(),
                res.toolSource(),
                res.messageId(),
                mutableMapOf(),
                MessageReceivedAction.CONTINUE
            )
    }

    override fun drop(): ScriptHttpResponse = ScriptHttpResponseImpl(
        res.clone(), // Clone to indicate that the response was modified
        req,
        annotations,
        toolSource,
        messageId,
        attachments,
        MessageReceivedAction.DROP
    )

    override fun intercept(): ScriptHttpResponse = ScriptHttpResponseImpl(
        res.clone(), // Clone to indicate that the response was modified
        req,
        annotations,
        toolSource,
        messageId,
        attachments,
        MessageReceivedAction.INTERCEPT
    )

    override fun action(): MessageReceivedAction = action

    @Internal
    override fun isRes(res: HttpResponse): Boolean = this.res === res

    override fun getAttachment(key: String): String? = attachments[key]
    override fun hasAttachment(key: String): Boolean = attachments.containsKey(key)
    override fun withAttachment(key: String, data: String): ScriptHttpResponse = ScriptHttpResponseImpl(
        res.clone(), // Clone to indicate that the response was modified
        req,
        annotations,
        toolSource,
        messageId,
        attachments.toMutableMap().apply { put(key, data) },
        action
    )

    override fun annotations(): Annotations = annotations

    override fun initiatingRequest(): HttpRequest = req

    override fun attributes(vararg types: AttributeType?): MutableList<Attribute> = res.attributes()

    override fun body(): ByteArray = res.body()

    override fun bodyOffset(): Int = res.bodyOffset()

    override fun bodyToString(): String = res.bodyToString()

    override fun bodyToJson(): Any? = fromJson(res.bodyToString())

    override fun contains(pattern: Pattern?): Boolean = res.contains(pattern)

    override fun httpVersion(): String = res.httpVersion()

    override fun contains(searchTerm: String?, caseSensitive: Boolean): Boolean =
        res.contains(searchTerm, caseSensitive)

    override fun cookie(name: String?): Cookie = res.cookie(name)

    override fun cookieValue(name: String?): String = res.cookieValue(name)

    override fun cookies(): MutableList<Cookie> = res.cookies()

    override fun copyToTempFile(): HttpResponse = res.copyToTempFile()

    override fun hasCookie(cookie: Cookie?): Boolean = res.hasCookie(cookie)

    override fun hasCookie(name: String?): Boolean = res.hasCookie(name)

    override fun hasHeader(header: HttpHeader?): Boolean = res.hasHeader(header)

    override fun hasHeader(name: String?): Boolean = res.hasHeader(name)

    override fun header(name: String?): HttpHeader = res.header(name)

    override fun hasHeader(name: String?, value: String?): Boolean = res.hasHeader(name, value)

    override fun headerValue(name: String?): String? = res.headerValue(name)

    override fun headers(): MutableList<HttpHeader> = res.headers()

    override fun inferredMimeType(): MimeType = res.inferredMimeType()

    override fun isStatusCodeClass(statusCodeClass: StatusCodeClass?): Boolean = res.isStatusCodeClass(statusCodeClass)

    override fun keywordCounts(vararg keywords: String?): MutableList<KeywordCount> = res.keywordCounts(*keywords)

    override fun markers(): MutableList<Marker> = res.markers()

    override fun messageId(): Int = messageId

    override fun mimeType(): MimeType = res.mimeType()

    override fun reasonPhrase(): String = res.reasonPhrase()

    override fun statedMimeType(): MimeType = res.statedMimeType()

    override fun statusCode(): Short = res.statusCode()

    override fun toByteArray(): ByteArray = res.toByteArray()

    override fun toolSource(): ToolSource = toolSource

    // These all need to be modified and can't be passthrough

    override fun withJson(value: Value): HttpResponse = withBody(toJson(value))

    override fun withBytes(signedOrUnsignedBytes: Value): HttpResponse =
        withBody(signedOrUnsignedBytes.toBurpByteArray())

    override fun withBody(body: String?): HttpResponse =
        ScriptHttpResponseImpl(res.withBody(body), req, annotations, toolSource, messageId, attachments, action)

    @Internal
    override fun withBody(body: ByteArray?): HttpResponse =
        ScriptHttpResponseImpl(res.withBody(body), req, annotations, toolSource, messageId, attachments, action)

    override fun withAddedHeader(header: HttpHeader?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withAddedHeader(header),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withAddedHeader(name: String?, value: String?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withAddedHeader(name, value),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withMarkers(vararg markers: Marker?): HttpResponse =
        ScriptHttpResponseImpl(res.withMarkers(*markers), req, annotations, toolSource, messageId, attachments, action)

    override fun withHttpVersion(httpVersion: String?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withHttpVersion(httpVersion),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withMarkers(markers: MutableList<Marker>?): HttpResponse =
        ScriptHttpResponseImpl(res.withMarkers(markers), req, annotations, toolSource, messageId, attachments, action)

    override fun withReasonPhrase(reasonPhrase: String?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withReasonPhrase(reasonPhrase),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withRemovedHeader(header: HttpHeader?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withRemovedHeader(header),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withRemovedHeader(name: String?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withRemovedHeader(name),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withStatusCode(statusCode: Short): HttpResponse =
        ScriptHttpResponseImpl(
            res.withStatusCode(statusCode),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withUpdatedHeader(header: HttpHeader?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withUpdatedHeader(header),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withUpdatedHeader(name: String?, value: String?): HttpResponse =
        ScriptHttpResponseImpl(
            res.withUpdatedHeader(name, value),
            req,
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )
}

// withStatusCode(statusCode()) Null pointer exception? what?
fun HttpResponse.clone(): HttpResponse = withBody(body())
