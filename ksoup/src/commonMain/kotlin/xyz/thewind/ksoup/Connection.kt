package xyz.thewind.ksoup

import xyz.thewind.ksoup.nodes.Document
import xyz.thewind.ksoup.parser.Parser

interface Connection {
    enum class Method {
        GET,
        POST,
        PUT,
        DELETE,
        PATCH,
        HEAD,
        OPTIONS
    }

    data class Proxy(
        val type: Type = Type.HTTP,
        val host: String,
        val port: Int
    ) {
        enum class Type {
            HTTP,
            SOCKS
        }
    }

    data class Credentials(
        val username: String,
        val password: String
    )

    data class AuthRequest(
        val url: String,
        val isProxy: Boolean
    )

    fun interface RequestAuthenticator {
        fun authenticate(request: AuthRequest): Credentials?
    }

    interface Base<T : Base<T>> {
        fun url(): String
        fun url(url: String): T
        fun header(name: String): String
        fun header(name: String, value: String): T
        fun headers(headers: Map<String, String>): T
        fun hasHeader(name: String): Boolean
        fun removeHeader(name: String): T
        fun headers(): Map<String, String>
        fun cookie(name: String): String
        fun cookie(name: String, value: String): T
        fun cookies(cookies: Map<String, String>): T
        fun hasCookie(name: String): Boolean
        fun removeCookie(name: String): T
        fun cookies(): Map<String, String>
    }

    interface Request : Base<Request> {
        fun method(): Method
        fun method(method: Method): Request
        fun timeout(): Int
        fun timeout(millis: Int): Request
        fun maxBodySize(): Int
        fun maxBodySize(bytes: Int): Request
        fun proxy(): Proxy?
        fun proxy(proxy: Proxy?): Request
        fun auth(): RequestAuthenticator?
        fun auth(authenticator: RequestAuthenticator?): Request
        fun validateTLSCertificates(): Boolean
        fun validateTLSCertificates(validate: Boolean): Request
        fun followRedirects(): Boolean
        fun followRedirects(followRedirects: Boolean): Request
        fun ignoreHttpErrors(): Boolean
        fun ignoreHttpErrors(ignoreHttpErrors: Boolean): Request
        fun ignoreContentType(): Boolean
        fun ignoreContentType(ignoreContentType: Boolean): Request
        fun requestBody(): String?
        fun requestBody(body: String?): Request
        fun data(): List<KeyVal>
        fun data(key: String): KeyVal?
        fun postDataCharset(): String
        fun postDataCharset(charset: String): Request
        fun parser(): Parser
        fun parser(parser: Parser): Request
    }

    interface Response : Base<Response> {
        fun statusCode(): Int
        fun statusMessage(): String
        fun charset(): String?
        fun contentType(): String?
        fun body(): String
        fun bodyAsBytes(): ByteArray
        fun parse(): Document
    }

    interface KeyVal {
        fun key(): String
        fun key(key: String): KeyVal
        fun value(): String
        fun value(value: String): KeyVal
        fun input(): ByteArray?
        fun input(bytes: ByteArray?): KeyVal
        fun fileName(): String?
        fun fileName(fileName: String?): KeyVal
        fun contentType(): String?
        fun contentType(contentType: String?): KeyVal
        fun hasInput(): Boolean = input() != null

        companion object {
            fun create(key: String, value: String): KeyVal = KeyValImpl(key, value)
            fun create(key: String, fileName: String, bytes: ByteArray, contentType: String? = null): KeyVal =
                KeyValImpl(key, "").fileName(fileName).input(bytes).contentType(contentType)
        }
    }

    fun request(): Request
    fun request(request: Request): Connection
    fun response(): Response?
    fun response(response: Response): Connection

    fun newRequest(): Connection
    fun newRequest(url: String): Connection

    fun url(url: String): Connection
    fun userAgent(userAgent: String): Connection
    fun referrer(referrer: String): Connection
    fun timeout(millis: Int): Connection
    fun method(method: Method): Connection
    fun proxy(host: String, port: Int): Connection
    fun proxy(proxy: Proxy): Connection
    fun auth(authenticator: RequestAuthenticator?): Connection
    fun validateTLSCertificates(validate: Boolean): Connection
    fun header(name: String, value: String): Connection
    fun headers(headers: Map<String, String>): Connection
    fun cookie(name: String, value: String): Connection
    fun cookies(cookies: Map<String, String>): Connection
    fun data(key: String, value: String): Connection
    fun data(key: String, fileName: String, input: ByteArray): Connection
    fun data(key: String, fileName: String, input: ByteArray, contentType: String): Connection
    fun data(data: Collection<KeyVal>): Connection
    fun data(vararg keyvals: String): Connection
    fun requestBody(body: String): Connection
    fun postDataCharset(charset: String): Connection
    fun maxBodySize(bytes: Int): Connection
    fun parser(parser: Parser): Connection
    fun followRedirects(followRedirects: Boolean): Connection
    fun ignoreHttpErrors(ignoreHttpErrors: Boolean): Connection
    fun ignoreContentType(ignoreContentType: Boolean): Connection

    fun execute(): Response
    fun get(): Document
    fun post(): Document
}

internal data class KeyValImpl(
    private var keyValue: String,
    private var valueValue: String,
    private var inputValue: ByteArray? = null,
    private var fileNameValue: String? = null,
    private var contentTypeValue: String? = null
) : Connection.KeyVal {
    override fun key(): String = keyValue

    override fun key(key: String): Connection.KeyVal {
        keyValue = key
        return this
    }

    override fun value(): String = valueValue

    override fun value(value: String): Connection.KeyVal {
        valueValue = value
        return this
    }

    override fun input(): ByteArray? = inputValue?.copyOf()

    override fun input(bytes: ByteArray?): Connection.KeyVal {
        inputValue = bytes?.copyOf()
        return this
    }

    override fun fileName(): String? = fileNameValue

    override fun fileName(fileName: String?): Connection.KeyVal {
        fileNameValue = fileName
        return this
    }

    override fun contentType(): String? = contentTypeValue

    override fun contentType(contentType: String?): Connection.KeyVal {
        contentTypeValue = contentType
        return this
    }
}
