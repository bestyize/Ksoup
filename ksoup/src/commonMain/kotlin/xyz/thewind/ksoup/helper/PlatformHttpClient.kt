package xyz.thewind.ksoup.helper

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout

internal expect fun createKsoupHttpClient(request: RequestData): HttpClient

internal fun HttpClientConfig<*>.configureKsoupHttpClient(request: RequestData) {
    expectSuccess = false
    followRedirects = request.followRedirects()
    install(HttpTimeout) {
        requestTimeoutMillis = request.timeout().toLong()
    }
    install(HttpRedirect)
}
