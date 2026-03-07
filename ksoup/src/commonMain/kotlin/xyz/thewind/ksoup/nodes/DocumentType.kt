package xyz.thewind.ksoup.nodes

class DocumentType(
    private var nameValue: String,
    private var publicIdValue: String = "",
    private var systemIdValue: String = "",
    private var pubSysKeyValue: String? = null
) : Node("#doctype") {
    fun name(): String = nameValue

    fun name(name: String): DocumentType {
        nameValue = name
        return this
    }

    fun publicId(): String = publicIdValue

    fun publicId(publicId: String): DocumentType {
        publicIdValue = publicId
        return this
    }

    fun systemId(): String = systemIdValue

    fun systemId(systemId: String): DocumentType {
        systemIdValue = systemId
        return this
    }

    fun pubSysKey(): String? = pubSysKeyValue

    fun pubSysKey(pubSysKey: String?): DocumentType {
        pubSysKeyValue = pubSysKey
        return this
    }

    override fun outerHtml(): String {
        val namePart = nameValue.trim()
        val pubSysKeyPart = pubSysKeyValue?.trim().orEmpty()
        return buildString {
            append("<!doctype")
            if (namePart.isNotEmpty()) {
                append(' ')
                append(namePart)
            }
            if (pubSysKeyPart.isNotEmpty()) {
                append(' ')
                append(pubSysKeyPart)
            }
            if (publicIdValue.isNotEmpty()) {
                append(' ')
                append('"')
                append(escapeHtml(publicIdValue))
                append('"')
            }
            if (systemIdValue.isNotEmpty()) {
                append(' ')
                append('"')
                append(escapeHtml(systemIdValue))
                append('"')
            }
            append('>')
        }
    }
}
