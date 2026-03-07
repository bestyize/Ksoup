package xyz.thewind.ksoup.nodes

class Comment(private var data: String) : Node("#comment") {
    fun getData(): String = data

    fun setData(data: String): Comment {
        this.data = data
        return this
    }

    fun isXmlDeclaration(): Boolean {
        val trimmed = data.trim()
        return trimmed.startsWith("?") || trimmed.startsWith("!")
    }

    fun asXmlDeclaration(): XmlDeclaration? {
        if (!isXmlDeclaration()) {
            return null
        }
        val trimmed = data.trim().trimStart('?', '!').trimEnd('?', '!')
        if (trimmed.isEmpty()) {
            return null
        }

        val nameEnd = trimmed.indexOfFirst { it.isWhitespace() }.let { if (it == -1) trimmed.length else it }
        val name = trimmed.substring(0, nameEnd)
        if (name.isEmpty()) {
            return null
        }
        val declaration = XmlDeclaration(name, declaration = data.trim().startsWith("!"))
        val attrPart = trimmed.substring(nameEnd).trim()
        parsePseudoAttributes(attrPart).forEach { (key, value) ->
            declaration.attr(key, value)
        }
        return declaration
    }

    override fun outerHtml(): String = "<!--$data-->"

    private fun parsePseudoAttributes(input: String): Map<String, String> {
        if (input.isEmpty()) {
            return emptyMap()
        }
        val attributes = linkedMapOf<String, String>()
        val pattern = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(['"])(.*?)\2""")
        pattern.findAll(input).forEach { match ->
            attributes[match.groupValues[1]] = match.groupValues[3]
        }
        return attributes
    }
}
