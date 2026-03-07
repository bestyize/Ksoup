package xyz.thewind.ksoup.nodes

class XmlDeclaration(
    private var nameValue: String,
    private val attributesValue: Attributes = Attributes(),
    private val declaration: Boolean = false
) : Node("#declaration") {
    fun isDeclaration(): Boolean = declaration

    fun name(): String = nameValue

    fun name(name: String): XmlDeclaration {
        nameValue = name
        return this
    }

    fun getWholeDeclaration(): String = buildString {
        append(nameValue)
        attributesValue.asMap().forEach { (key, value) ->
            append(' ')
            append(key)
            if (value.isNotEmpty()) {
                append("=\"")
                append(escapeHtml(value))
                append('"')
            }
        }
    }

    override fun attr(attributeKey: String): String = attributesValue[attributeKey]

    override fun attr(attributeKey: String, attributeValue: String): XmlDeclaration {
        attributesValue[attributeKey] = attributeValue
        return this
    }

    override fun hasAttr(attributeKey: String): Boolean = attributesValue.hasKey(attributeKey)

    override fun removeAttr(attributeKey: String): XmlDeclaration {
        attributesValue.remove(attributeKey)
        return this
    }

    override fun attributesSize(): Int = attributesValue.size()

    fun attributes(): Attributes = attributesValue

    override fun outerHtml(): String {
        val marker = if (declaration) "!" else "?"
        return "<$marker${getWholeDeclaration()}$marker>"
    }
}
