package xyz.thewind.ksoup.helper

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Url
import xyz.thewind.ksoup.Connection
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal actual fun createKsoupHttpClient(request: RequestData): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    followRedirects = request.followRedirects()
    install(HttpTimeout) {
        requestTimeoutMillis = request.timeout().toLong()
    }
    install(HttpRedirect)
    engine {
        request.proxy()?.let { proxy ->
            this.proxy = when (proxy.type) {
                Connection.Proxy.Type.HTTP -> ProxyBuilder.http(Url("http://${proxy.host}:${proxy.port}"))
                Connection.Proxy.Type.SOCKS -> ProxyBuilder.socks(host = proxy.host, port = proxy.port)
            }
        }

        if (!request.validateTLSCertificates()) {
            https {
                trustManager = insecureTrustManager
            }
        }
    }
}

private val insecureTrustManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private val insecureSslContext: SSLContext by lazy {
    SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(insecureTrustManager), SecureRandom())
    }
}
