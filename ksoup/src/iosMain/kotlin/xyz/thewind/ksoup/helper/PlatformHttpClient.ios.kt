package xyz.thewind.ksoup.helper

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Url
import xyz.thewind.ksoup.Connection

internal actual fun createKsoupHttpClient(request: RequestData): HttpClient = HttpClient(Darwin) {
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
    }
}
