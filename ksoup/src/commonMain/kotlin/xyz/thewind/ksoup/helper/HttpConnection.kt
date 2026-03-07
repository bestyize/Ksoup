package xyz.thewind.ksoup.helper

import io.ktor.client.request.basicAuth
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Headers
import io.ktor.http.encodeURLParameter
import io.ktor.http.headers
import kotlinx.coroutines.runBlocking
import xyz.thewind.ksoup.Connection
import xyz.thewind.ksoup.HttpStatusException
import xyz.thewind.ksoup.Jsoup
import xyz.thewind.ksoup.UnsupportedMimeTypeException
import xyz.thewind.ksoup.nodes.Document
import xyz.thewind.ksoup.parser.Parser

internal class HttpConnection private constructor(
    private val session: SessionState,
    private var req: RequestData
) : Connection {
    private var res: ResponseData? = null

    override fun request(): Connection.Request = req

    override fun request(request: Connection.Request): Connection {
        req = (request as? RequestData)?.copyDeep() ?: throw IllegalArgumentException("Unsupported request implementation")
        return this
    }

    override fun response(): Connection.Response? = res

    override fun response(response: Connection.Response): Connection {
        res = response as? ResponseData ?: throw IllegalArgumentException("Unsupported response implementation")
        return this
    }

    override fun newRequest(): Connection = HttpConnection(session, req.copyDeep().clearRequestState())

    override fun newRequest(url: String): Connection = newRequest().url(url)

    override fun url(url: String): Connection = apply { req.url(url) }

    override fun userAgent(userAgent: String): Connection = header(HttpHeaders.UserAgent, userAgent)

    override fun referrer(referrer: String): Connection = header(HttpHeaders.Referrer, referrer)

    override fun timeout(millis: Int): Connection = apply { req.timeout(millis) }

    override fun method(method: Connection.Method): Connection = apply { req.method(method) }

    override fun proxy(host: String, port: Int): Connection = apply {
        req.proxy(Connection.Proxy(host = host, port = port))
    }

    override fun proxy(proxy: Connection.Proxy): Connection = apply { req.proxy(proxy) }

    override fun auth(authenticator: Connection.RequestAuthenticator?): Connection = apply { req.auth(authenticator) }

    override fun validateTLSCertificates(validate: Boolean): Connection = apply { req.validateTLSCertificates(validate) }

    override fun header(name: String, value: String): Connection = apply { req.header(name, value) }

    override fun headers(headers: Map<String, String>): Connection = apply { req.headers(headers) }

    override fun cookie(name: String, value: String): Connection = apply { req.cookie(name, value) }

    override fun cookies(cookies: Map<String, String>): Connection = apply { req.cookies(cookies) }

    override fun data(key: String, value: String): Connection = apply { req.dataValues.add(xyz.thewind.ksoup.Connection.KeyVal.create(key, value)) }

    override fun data(key: String, fileName: String, input: ByteArray): Connection = apply {
        req.dataValues.add(Connection.KeyVal.create(key, fileName, input))
    }

    override fun data(key: String, fileName: String, input: ByteArray, contentType: String): Connection = apply {
        req.dataValues.add(Connection.KeyVal.create(key, fileName, input, contentType))
    }

    override fun data(data: Collection<Connection.KeyVal>): Connection = apply {
        data.forEach { keyVal ->
            req.dataValues.add(
                Connection.KeyVal.create(keyVal.key(), keyVal.value())
                    .fileName(keyVal.fileName())
                    .input(keyVal.input())
                    .contentType(keyVal.contentType())
            )
        }
    }

    override fun data(vararg keyvals: String): Connection = apply {
        require(keyvals.size % 2 == 0) { "Must supply an even number of key/value pairs" }
        var index = 0
        while (index < keyvals.size) {
            data(keyvals[index], keyvals[index + 1])
            index += 2
        }
    }

    override fun requestBody(body: String): Connection = apply { req.requestBody(body) }

    override fun postDataCharset(charset: String): Connection = apply { req.postDataCharset(charset) }

    override fun maxBodySize(bytes: Int): Connection = apply { req.maxBodySize(bytes) }

    override fun parser(parser: Parser): Connection = apply { req.parser(parser) }

    override fun followRedirects(followRedirects: Boolean): Connection = apply { req.followRedirects(followRedirects) }

    override fun ignoreHttpErrors(ignoreHttpErrors: Boolean): Connection = apply { req.ignoreHttpErrors(ignoreHttpErrors) }

    override fun ignoreContentType(ignoreContentType: Boolean): Connection = apply { req.ignoreContentType(ignoreContentType) }

    override fun execute(): Connection.Response = runBlocking {
        val execRequest = req.copyDeep()
        session.cookies.forEach { (name, value) ->
            if (!execRequest.hasCookie(name)) {
                execRequest.cookie(name, value)
            }
        }

        val response = KsoupTransport.execute(execRequest)
        session.cookies.putAll(response.cookies())
        res = response
        if (!execRequest.ignoreHttpErrors() && response.statusCode() >= 400) {
            throw HttpStatusException(
                "HTTP error fetching URL. Status=${response.statusCode()}, URL=[${response.url()}]",
                response.statusCode(),
                response.url()
            )
        }
        response
    }

    override fun get(): Document {
        req.method(Connection.Method.GET)
        return execute().parse()
    }

    override fun post(): Document {
        req.method(Connection.Method.POST)
        return execute().parse()
    }

    companion object {
        fun connect(url: String): Connection = HttpConnection(SessionState(), RequestData(url)).url(url)

        fun newSession(): Connection = HttpConnection(SessionState(), RequestData(""))

        internal fun fromContext(request: RequestData, response: ResponseData): Connection =
            HttpConnection(
                SessionState(
                    linkedMapOf<String, String>().apply {
                        putAll(request.cookies())
                        putAll(response.cookies())
                    }
                ),
                request.copyDeep()
            ).response(response)
    }
}

internal data class SessionState(
    val cookies: LinkedHashMap<String, String> = linkedMapOf()
)

internal abstract class BaseData<T : Connection.Base<T>>(
    private var urlValue: String
) : Connection.Base<T> {
    private val headersMap = linkedMapOf<String, Pair<String, String>>()
    private val cookiesMap = linkedMapOf<String, String>()

    override fun url(): String = urlValue

    override fun url(url: String): T = self().also { urlValue = url }

    override fun header(name: String): String = headersMap[normalizeHeaderName(name)]?.second.orEmpty()

    override fun header(name: String, value: String): T = self().also {
        headersMap[normalizeHeaderName(name)] = name to value
    }

    override fun headers(headers: Map<String, String>): T = self().also {
        headers.forEach { (name, value) -> header(name, value) }
    }

    override fun hasHeader(name: String): Boolean = normalizeHeaderName(name) in headersMap

    override fun removeHeader(name: String): T = self().also {
        headersMap.remove(normalizeHeaderName(name))
    }

    override fun headers(): Map<String, String> = headersMap.values.associate { it.first to it.second }

    override fun cookie(name: String): String = cookiesMap[name].orEmpty()

    override fun cookie(name: String, value: String): T = self().also {
        cookiesMap[name] = value
    }

    override fun cookies(cookies: Map<String, String>): T = self().also {
        cookies.forEach { (name, value) -> cookie(name, value) }
    }

    override fun hasCookie(name: String): Boolean = name in cookiesMap

    override fun removeCookie(name: String): T = self().also {
        cookiesMap.remove(name)
    }

    override fun cookies(): Map<String, String> = cookiesMap.toMap()

    protected abstract fun self(): T

    protected fun copyBaseTo(target: BaseData<T>) {
        target.url(url())
        headersMap.values.forEach { (name, value) -> target.header(name, value) }
        cookiesMap.forEach { (name, value) -> target.cookie(name, value) }
    }

    private fun normalizeHeaderName(name: String): String = name.lowercase()
}

internal class RequestData(url: String) : BaseData<Connection.Request>(url), Connection.Request {
    private var methodValue = Connection.Method.GET
    private var timeoutValue = 30_000
    private var maxBodySizeValue = 2 * 1024 * 1024
    private var proxyValue: Connection.Proxy? = null
    private var authenticatorValue: Connection.RequestAuthenticator? = null
    private var validateTLSCertificatesValue = true
    private var followRedirectsValue = true
    private var ignoreHttpErrorsValue = false
    private var ignoreContentTypeValue = false
    private var requestBodyValue: String? = null
    private var postDataCharsetValue = "UTF-8"
    private var parserValue: Parser = Parser.htmlParser()
    internal val dataValues = mutableListOf<Connection.KeyVal>()

    override fun method(): Connection.Method = methodValue

    override fun method(method: Connection.Method): Connection.Request = self().also { methodValue = method }

    override fun timeout(): Int = timeoutValue

    override fun timeout(millis: Int): Connection.Request = self().also { timeoutValue = millis }

    override fun maxBodySize(): Int = maxBodySizeValue

    override fun maxBodySize(bytes: Int): Connection.Request = self().also { maxBodySizeValue = bytes }

    override fun proxy(): Connection.Proxy? = proxyValue

    override fun proxy(proxy: Connection.Proxy?): Connection.Request = self().also { proxyValue = proxy }

    override fun auth(): Connection.RequestAuthenticator? = authenticatorValue

    override fun auth(authenticator: Connection.RequestAuthenticator?): Connection.Request = self().also {
        authenticatorValue = authenticator
    }

    override fun validateTLSCertificates(): Boolean = validateTLSCertificatesValue

    override fun validateTLSCertificates(validate: Boolean): Connection.Request = self().also {
        validateTLSCertificatesValue = validate
    }

    override fun followRedirects(): Boolean = followRedirectsValue

    override fun followRedirects(followRedirects: Boolean): Connection.Request = self().also {
        followRedirectsValue = followRedirects
    }

    override fun ignoreHttpErrors(): Boolean = ignoreHttpErrorsValue

    override fun ignoreHttpErrors(ignoreHttpErrors: Boolean): Connection.Request = self().also {
        ignoreHttpErrorsValue = ignoreHttpErrors
    }

    override fun ignoreContentType(): Boolean = ignoreContentTypeValue

    override fun ignoreContentType(ignoreContentType: Boolean): Connection.Request = self().also {
        ignoreContentTypeValue = ignoreContentType
    }

    override fun requestBody(): String? = requestBodyValue

    override fun requestBody(body: String?): Connection.Request = self().also { requestBodyValue = body }

    override fun data(): List<Connection.KeyVal> = dataValues.toList()

    override fun data(key: String): Connection.KeyVal? = dataValues.firstOrNull { it.key() == key }

    override fun postDataCharset(): String = postDataCharsetValue

    override fun postDataCharset(charset: String): Connection.Request = self().also { postDataCharsetValue = charset }

    override fun parser(): Parser = parserValue

    override fun parser(parser: Parser): Connection.Request = self().also { parserValue = parser }

    override fun self(): Connection.Request = this

    fun copyDeep(): RequestData = RequestData(url()).also { target ->
        copyBaseTo(target)
        target.method(method())
        target.timeout(timeout())
        target.maxBodySize(maxBodySize())
        target.proxy(proxy())
        target.auth(auth())
        target.validateTLSCertificates(validateTLSCertificates())
        target.followRedirects(followRedirects())
        target.ignoreHttpErrors(ignoreHttpErrors())
        target.ignoreContentType(ignoreContentType())
        target.requestBody(requestBody())
        target.postDataCharset(postDataCharset())
        target.parser(parser())
        dataValues.forEach {
            target.dataValues.add(
                Connection.KeyVal.create(it.key(), it.value())
                    .fileName(it.fileName())
                    .input(it.input())
                    .contentType(it.contentType())
            )
        }
    }

    fun clearRequestState(): RequestData = apply {
        requestBody(null)
        dataValues.clear()
        method(Connection.Method.GET)
    }
}

internal class ResponseData(
    url: String,
    private var statusCodeValue: Int = 0,
    private var statusMessageValue: String = "",
    private var charsetValue: String? = null,
    private var contentTypeValue: String? = null,
    private var bodyValue: String = "",
    private var bodyBytesValue: ByteArray = byteArrayOf(),
    private val ignoreContentTypeValue: Boolean = false,
    private val parserValue: Parser = Parser.htmlParser(),
    private val requestValue: RequestData? = null
) : BaseData<Connection.Response>(url), Connection.Response {
    override fun statusCode(): Int = statusCodeValue

    override fun statusMessage(): String = statusMessageValue

    override fun charset(): String? = charsetValue

    override fun contentType(): String? = contentTypeValue

    override fun body(): String = bodyValue

    override fun bodyAsBytes(): ByteArray = bodyBytesValue.copyOf()

    override fun parse(): Document {
        if (!ignoreContentTypeValue && !isParseableContentType(contentType())) {
            throw UnsupportedMimeTypeException(
                "Unhandled content type. Must be text/*, */xml, or */html. Content type=[${contentType()}], URL=[${url()}]",
                contentType(),
                url()
            )
        }
        return parserValue.parseInput(body(), url()).also { document ->
            requestValue?.let { document.connection(HttpConnection.fromContext(it, this)) }
        }
    }

    override fun self(): Connection.Response = this

    companion object {
        fun fromTransport(response: TransportResponse, request: RequestData): ResponseData =
            ResponseData(
                url = response.url,
                statusCodeValue = response.statusCode,
                statusMessageValue = response.statusMessage,
                charsetValue = parseCharset(response.contentType),
                contentTypeValue = response.contentType,
                bodyValue = response.body,
                bodyBytesValue = response.bodyBytes,
                ignoreContentTypeValue = request.ignoreContentType(),
                parserValue = request.parser(),
                requestValue = request.copyDeep()
            ).also { target ->
                response.headers.forEach { (name, value) -> target.header(name, value) }
                response.cookies.forEach { (name, value) -> target.cookie(name, value) }
            }

        private fun parseCharset(contentType: String?): String? {
            val value = contentType ?: return null
            val marker = "charset="
            val index = value.indexOf(marker, ignoreCase = true)
            if (index == -1) {
                return null
            }
            return value.substring(index + marker.length).substringBefore(';').trim().ifEmpty { null }
        }

        private fun isParseableContentType(contentType: String?): Boolean {
            if (contentType == null) {
                return true
            }
            val mime = contentType.substringBefore(';').trim().lowercase()
            return mime.startsWith("text/") || mime.endsWith("/xml") || mime.endsWith("+xml") || mime.endsWith("/html")
        }
    }
}

internal data class TransportResponse(
    val url: String,
    val statusCode: Int,
    val statusMessage: String,
    val contentType: String?,
    val headers: Map<String, String>,
    val cookies: Map<String, String>,
    val body: String,
    val bodyBytes: ByteArray
)

internal fun interface KsoupTransportExecutor {
    suspend fun execute(request: RequestData): ResponseData
}

internal object KsoupTransport {
    var executor: KsoupTransportExecutor = KsoupTransportExecutor { request ->
        val response = performKtorRequest(request)
        ResponseData.fromTransport(response, request)
    }

    suspend fun execute(request: RequestData): ResponseData = executor.execute(request)
}

private suspend fun performKtorRequest(request: RequestData): TransportResponse {
    val client = createKsoupHttpClient(request)
    try {
        val queryString = if (request.method() == Connection.Method.GET || request.method() == Connection.Method.HEAD) {
            encodeForm(request.data())
        } else {
            ""
        }
        val requestUrl = buildUrl(request.url(), queryString)
        val response = client.prepareRequest {
            url(requestUrl)
            method = request.method().toKtor()
            headers {
                request.headers().forEach { (name, value) -> append(name, value) }
                val mergedCookies = linkedMapOf<String, String>()
                request.cookies().forEach { (name, value) -> mergedCookies[name] = value }
                if (mergedCookies.isNotEmpty()) {
                    append(HttpHeaders.Cookie, mergedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }

            request.auth()?.authenticate(Connection.AuthRequest(request.url(), isProxy = false))?.let { credentials ->
                basicAuth(credentials.username, credentials.password)
            }

            request.auth()?.authenticate(Connection.AuthRequest(request.url(), isProxy = true))?.let { credentials ->
                header(
                    HttpHeaders.ProxyAuthorization,
                    "Basic " + "${credentials.username}:${credentials.password}".encodeToByteArray().encodeBase64()
                )
            }

            if (request.method() != Connection.Method.GET && request.method() != Connection.Method.HEAD) {
                when {
                    request.requestBody() != null -> setBody(request.requestBody()!!)
                    request.data().any { it.hasInput() } -> {
                        setBody(
                            MultiPartFormDataContent(
                                formData {
                                    request.data().forEach { keyVal ->
                                        if (keyVal.hasInput()) {
                                            append(
                                                keyVal.key(),
                                                keyVal.input()!!,
                                                Headers.build {
                                                    append(
                                                        HttpHeaders.ContentDisposition,
                                                        buildString {
                                                            append("form-data; name=\"")
                                                            append(keyVal.key())
                                                            append("\"")
                                                            keyVal.fileName()?.let {
                                                                append("; filename=\"")
                                                                append(it)
                                                                append("\"")
                                                            }
                                                        }
                                                    )
                                                    keyVal.contentType()?.let { append(HttpHeaders.ContentType, it) }
                                                }
                                            )
                                        } else {
                                            append(keyVal.key(), keyVal.value())
                                        }
                                    }
                                }
                            )
                        )
                    }
                    request.data().isNotEmpty() -> {
                        if (!request.hasHeader(HttpHeaders.ContentType)) {
                            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=${request.postDataCharset()}")
                        }
                        setBody(encodeForm(request.data()))
                    }
                }
            }
        }.execute()

        val headers = linkedMapOf<String, String>()
        response.headers.forEach { name, values ->
            headers[name] = values.joinToString(", ")
        }
        val bodyBytes = response.bodyAsBytes()
        val limitedBytes = if (request.maxBodySize() > 0 && bodyBytes.size > request.maxBodySize()) {
            bodyBytes.copyOf(request.maxBodySize())
        } else {
            bodyBytes
        }

        return TransportResponse(
            url = response.call.request.url.toString(),
            statusCode = response.status.value,
            statusMessage = response.status.description,
            contentType = response.headers[HttpHeaders.ContentType],
            headers = headers,
            cookies = extractCookies(response.headers.getAll(HttpHeaders.SetCookie).orEmpty()),
            body = limitedBytes.decodeToString(),
            bodyBytes = limitedBytes
        )
    } finally {
        client.close()
    }
}

private fun extractCookies(headerValues: List<String>): Map<String, String> {
    val cookies = linkedMapOf<String, String>()
    headerValues.forEach { value ->
        val pair = value.substringBefore(';')
        val index = pair.indexOf('=')
        if (index > 0) {
            cookies[pair.substring(0, index)] = pair.substring(index + 1)
        }
    }
    return cookies
}

private fun encodeForm(data: List<Connection.KeyVal>): String = data.joinToString("&") { keyVal ->
    "${keyVal.key().encodeURLParameter()}=${keyVal.value().encodeURLParameter()}"
}

private fun buildUrl(url: String, queryString: String): String {
    if (queryString.isEmpty()) {
        return url
    }
    return if ('?' in url) "$url&$queryString" else "$url?$queryString"
}

private fun Connection.Method.toKtor(): HttpMethod = when (this) {
    Connection.Method.GET -> HttpMethod.Get
    Connection.Method.POST -> HttpMethod.Post
    Connection.Method.PUT -> HttpMethod.Put
    Connection.Method.DELETE -> HttpMethod.Delete
    Connection.Method.PATCH -> HttpMethod.Patch
    Connection.Method.HEAD -> HttpMethod.Head
    Connection.Method.OPTIONS -> HttpMethod.Options
}

private fun ByteArray.encodeBase64(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = StringBuilder((size + 2) / 3 * 4)
    var index = 0
    while (index < size) {
        val b0 = this[index].toInt() and 0xff
        val b1 = if (index + 1 < size) this[index + 1].toInt() and 0xff else 0
        val b2 = if (index + 2 < size) this[index + 2].toInt() and 0xff else 0

        output.append(alphabet[b0 shr 2])
        output.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
        output.append(if (index + 1 < size) alphabet[((b1 and 0x0f) shl 2) or (b2 shr 6)] else '=')
        output.append(if (index + 2 < size) alphabet[b2 and 0x3f] else '=')
        index += 3
    }
    return output.toString()
}
