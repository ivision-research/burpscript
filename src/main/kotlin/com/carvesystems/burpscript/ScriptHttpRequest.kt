package com.carvesystems.burpscript

import burp.api.montoya.core.*
import burp.api.montoya.core.ByteArray
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.handler.HttpRequestToBeSent
import burp.api.montoya.http.message.ContentType
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.params.HttpParameter
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.requests.HttpTransformation
import burp.api.montoya.proxy.MessageReceivedAction
import burp.api.montoya.proxy.http.InterceptedRequest
import com.carvesystems.burpscript.interop.*
import org.graalvm.polyglot.Value
import java.util.regex.Pattern

interface ScriptHttpRequest : HttpRequestToBeSent {
    /** True if the response contains an attachment with a certain file name */
    @ScriptApi
    fun hasAttachment(key: String): Boolean

    /** Gets an attachment, if it exists, as a string */
    @ScriptApi
    fun getAttachment(key: String): String?

    /** Add an attachment to the request */
    @ScriptApi
    fun withAttachment(key: String, data: String): ScriptHttpRequest

    /** Add a query parameter to the request */
    @ScriptApi
    fun withQueryParameter(key: String, data: String): ScriptHttpRequest

    /**
     * Parse body as JSON
     * ScriptMap | List | String | Number | Boolean | null
     */
    @ScriptApi
    fun bodyToJson(): Any?

    /**
     *
     */
    @ScriptApi
    fun bodyToBytes(): UnsignedByteArray

    /**
     * Serialize `value` to JSON and set the body
     */
    @ScriptApi
    fun withJson(value: Value): HttpRequest

    /**
     * Set the body to signed or unsigned bytes
     */
    fun withBytes(bytes: AnyBinary): HttpRequest

    /**
     * Drop this response, do not send this response to the server.
     * Not that this is only applicable if the script is handling a request from the proxy.
     * i.e. the script has been configured as "Proxy Only", or req.toolSource() == ToolType.PROXY
     */
    fun drop(): ScriptHttpRequest

    /**
     * Cause the proxy to present this request for review and editing in the Intercept tab
     * Not that this is only applicable if the script is handling a response from the proxy.
     * i.e. the script has been configured as "Proxy Only", or req.toolSource() == ToolType.PROXY
     */
    fun intercept(): ScriptHttpRequest

    fun action(): MessageReceivedAction

    @Internal
    fun isReq(req: HttpRequest): Boolean
}

class ScriptHttpRequestImpl(
    private val req: HttpRequest,
    private val annotations: Annotations,
    private val toolSource: ToolSource,
    private val messageId: Int,
    private val attachments: MutableMap<String, String>,
    private val action: MessageReceivedAction,
) : ScriptHttpRequest {

    companion object {

        fun wrap(req: InterceptedRequest): ScriptHttpRequest =
            ScriptHttpRequestImpl(
                req,
                // This is a reference, we want to be able to modify the annotations of the original req
                req.annotations(),
                SimpleToolSource(ToolType.PROXY),
                req.messageId(),
                mutableMapOf(),
                MessageReceivedAction.CONTINUE
            )

        fun wrap(req: HttpRequestToBeSent): ScriptHttpRequest =
            ScriptHttpRequestImpl(
                req,
                // This is a reference, we want to be able to modify the annotations of the original req
                req.annotations(),
                req.toolSource(),
                req.messageId(),
                mutableMapOf(),
                MessageReceivedAction.CONTINUE
            )
    }

    override fun drop(): ScriptHttpRequest = ScriptHttpRequestImpl(
        req.clone(), // Clone to indicate that the request was modified
        annotations,
        toolSource,
        messageId,
        attachments,
        MessageReceivedAction.DROP
    )

    override fun intercept(): ScriptHttpRequest = ScriptHttpRequestImpl(
        req.clone(), // Clone to indicate that the response was modified
        annotations,
        toolSource,
        messageId,
        attachments,
        MessageReceivedAction.INTERCEPT
    )

    override fun action(): MessageReceivedAction = action

    @Internal
    override fun isReq(req: HttpRequest): Boolean = this.req === req

    override fun getAttachment(key: String): String? = attachments[key]
    override fun hasAttachment(key: String): Boolean = attachments.containsKey(key)
    override fun withAttachment(key: String, data: String): ScriptHttpRequest = ScriptHttpRequestImpl(
        req.clone(), // Clone to indicate that the request was modified
        annotations,
        toolSource,
        messageId,
        attachments.toMutableMap().apply { put(key, data) },
        action
    )

    override fun annotations(): Annotations = annotations

    override fun body(): ByteArray = req.body()

    override fun bodyOffset(): Int = req.bodyOffset()

    override fun bodyToString(): String = req.bodyToString()

    override fun bodyToJson(): Any? = fromJson(req.bodyToString())

    override fun bodyToBytes(): UnsignedByteArray = req.body().toUnsignedByteArray()

    override fun contains(pattern: Pattern?): Boolean = req.contains(pattern)

    override fun contains(searchTerm: String?, caseSensitive: Boolean): Boolean =
        req.contains(searchTerm, caseSensitive)

    override fun contentType(): ContentType = req.contentType()

    override fun copyToTempFile(): HttpRequest = req.copyToTempFile()

    override fun hasHeader(name: String?): Boolean = req.hasHeader(name)

    override fun hasHeader(header: HttpHeader?): Boolean = req.hasHeader(header)

    override fun hasHeader(name: String?, value: String?): Boolean = req.hasHeader(name)

    override fun hasParameter(parameter: HttpParameter?): Boolean = req.hasParameter(parameter)

    override fun hasParameters(): Boolean = req.hasParameters()

    override fun hasParameter(name: String?, type: HttpParameterType?): Boolean = req.hasParameter(name, type)

    override fun header(name: String?): HttpHeader = req.header(name)

    override fun hasParameters(type: HttpParameterType?): Boolean = req.hasParameters()

    override fun headerValue(name: String?): String? = req.headerValue(name)

    override fun headers(): MutableList<HttpHeader> = req.headers()

    override fun httpService(): HttpService = req.httpService()

    override fun httpVersion(): String = req.httpVersion()

    override fun isInScope(): Boolean = req.isInScope

    override fun markers(): MutableList<Marker> = req.markers()

    override fun messageId(): Int = messageId

    override fun method(): String = req.method()

    override fun parameters(): MutableList<ParsedHttpParameter> = req.parameters()

    override fun parameter(name: String?): ParsedHttpParameter = req.parameter(name)

    override fun parameter(name: String?, type: HttpParameterType?): ParsedHttpParameter = req.parameter(name, type)

    override fun parameterValue(name: String?): String = req.parameterValue(name)

    override fun parameterValue(name: String?, type: HttpParameterType?): String = req.parameterValue(name, type)

    override fun parameters(type: HttpParameterType?): MutableList<ParsedHttpParameter> = req.parameters(type)

    override fun path(): String? = req.path()

    override fun query(): String? = req.query()

    override fun fileExtension(): String? = req.fileExtension()

    override fun pathWithoutQuery(): String? = req.pathWithoutQuery()

    override fun toByteArray(): ByteArray = req.toByteArray()

    override fun toolSource(): ToolSource = toolSource

    override fun url(): String = req.url()


    // These all need to be modified and can't be passthrough

    override fun withJson(value: Value): HttpRequest = withBody(toJson(value))

    override fun withBytes(bytes: AnyBinary): HttpRequest =
        withBody(bytes.asAnyBinaryToByteArray().toBurpByteArray())

    override fun withBody(body: String?): HttpRequest =
        ScriptHttpRequestImpl(req.withBody(body), annotations, toolSource, messageId, attachments, action)

    @Internal
    override fun withBody(body: ByteArray?): HttpRequest =
        ScriptHttpRequestImpl(req.withBody(body), annotations, toolSource, messageId, attachments, action)

    override fun withDefaultHeaders(): HttpRequest =
        ScriptHttpRequestImpl(req.withDefaultHeaders(), annotations, toolSource, messageId, attachments, action)

    override fun withHeader(header: HttpHeader?): HttpRequest =
        ScriptHttpRequestImpl(req.withHeader(header), annotations, toolSource, messageId, attachments, action)

    override fun withHeader(name: String?, value: String?): HttpRequest =
        ScriptHttpRequestImpl(req.withHeader(name, value), annotations, toolSource, messageId, attachments, action)

    override fun withParameter(parameters: HttpParameter?): HttpRequest =
        ScriptHttpRequestImpl(req.withParameter(parameters), annotations, toolSource, messageId, attachments, action)

    override fun withService(service: HttpService?): HttpRequest =
        ScriptHttpRequestImpl(req.withService(service), annotations, toolSource, messageId, attachments, action)

    override fun withPath(path: String?): HttpRequest =
        ScriptHttpRequestImpl(req.withPath(path), annotations, toolSource, messageId, attachments, action)

    override fun withMethod(method: String?): HttpRequest =
        ScriptHttpRequestImpl(req.withMethod(method), annotations, toolSource, messageId, attachments, action)

    override fun withQueryParameter(key: String, data: String): ScriptHttpRequest {
        val newParam = HttpParameter.urlParameter(key, data)
        return ScriptHttpRequestImpl(
            req.withParameter(newParam),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )
    }

    override fun withRemovedHeaders(headers: List<HttpHeader>): HttpRequest =
         ScriptHttpRequestImpl(
            req.withRemovedHeaders(headers),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withRemovedHeaders(vararg headers: HttpHeader): HttpRequest =
         ScriptHttpRequestImpl(
            req.withRemovedHeaders(*headers),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withUpdatedHeaders(vararg headers: HttpHeader): HttpRequest =
         ScriptHttpRequestImpl(
            req.withUpdatedHeaders(*headers),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withUpdatedHeaders(headers: List<HttpHeader>): HttpRequest =
         ScriptHttpRequestImpl(
            req.withUpdatedHeaders(headers),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withAddedHeaders(headers: List<HttpHeader>): HttpRequest =
         ScriptHttpRequestImpl(
            req.withAddedHeaders(headers),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withAddedHeaders(vararg headers: HttpHeader): HttpRequest =
         ScriptHttpRequestImpl(
            req.withAddedHeaders(*headers),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )
       
    override fun withAddedHeader(header: HttpHeader?): HttpRequest =
        ScriptHttpRequestImpl(req.withAddedHeader(header), annotations, toolSource, messageId, attachments, action)

    override fun withUpdatedHeader(name: String?, value: String?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withUpdatedHeader(name, value),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withUpdatedHeader(header: HttpHeader?): HttpRequest =
        ScriptHttpRequestImpl(req.withUpdatedHeader(header), annotations, toolSource, messageId, attachments, action)

    override fun withRemovedHeader(name: String?): HttpRequest =
        ScriptHttpRequestImpl(req.withRemovedHeader(name), annotations, toolSource, messageId, attachments, action)

    override fun withRemovedHeader(header: HttpHeader?): HttpRequest =
        ScriptHttpRequestImpl(req.withRemovedHeader(header), annotations, toolSource, messageId, attachments, action)

    override fun withMarkers(markers: MutableList<Marker>?): HttpRequest =
        ScriptHttpRequestImpl(req.withMarkers(markers), annotations, toolSource, messageId, attachments, action)

    override fun withMarkers(vararg markers: Marker?): HttpRequest =
        ScriptHttpRequestImpl(req.withMarkers(*markers), annotations, toolSource, messageId, attachments, action)

    override fun withAddedHeader(name: String?, value: String?): HttpRequest =
        ScriptHttpRequestImpl(req.withAddedHeader(name, value), annotations, toolSource, messageId, attachments, action)

    override fun withAddedParameters(vararg parameters: HttpParameter?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withAddedParameters(*parameters),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withRemovedParameters(parameters: MutableList<out HttpParameter>?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withRemovedParameters(parameters),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withRemovedParameters(vararg parameters: HttpParameter?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withRemovedParameters(*parameters),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withUpdatedParameters(parameters: MutableList<out HttpParameter>?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withUpdatedParameters(parameters),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withUpdatedParameters(vararg parameters: HttpParameter?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withUpdatedParameters(*parameters),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withTransformationApplied(transformation: HttpTransformation?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withTransformationApplied(transformation),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

    override fun withAddedParameters(parameters: MutableList<out HttpParameter>?): HttpRequest =
        ScriptHttpRequestImpl(
            req.withAddedParameters(parameters),
            annotations,
            toolSource,
            messageId,
            attachments,
            action
        )

}

private fun HttpRequest.clone() : HttpRequest = withMethod(method())
