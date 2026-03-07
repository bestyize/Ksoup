package xyz.thewind.ksoup.helper

import io.ktor.client.HttpClient

internal expect fun createKsoupHttpClient(request: RequestData): HttpClient
