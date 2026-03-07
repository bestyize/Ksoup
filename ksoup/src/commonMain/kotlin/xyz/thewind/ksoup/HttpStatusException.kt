package xyz.thewind.ksoup

class HttpStatusException(
    message: String,
    val statusCode: Int,
    val url: String
) : Exception(message)
