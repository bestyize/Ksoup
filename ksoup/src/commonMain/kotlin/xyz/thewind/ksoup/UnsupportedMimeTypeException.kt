package xyz.thewind.ksoup

class UnsupportedMimeTypeException(
    message: String,
    val mimeType: String?,
    val url: String
) : Exception(message)
