package xyz.thewind.ksoup.nodes

import xyz.thewind.ksoup.parser.Parser

open class TextNode(private var textValue: String) : Node("#text") {
    fun text(text: String): TextNode {
        textValue = text
        return this
    }

    fun splitText(offset: Int): TextNode {
        require(offset in 0..textValue.length) { "Split offset must be between 0 and ${textValue.length}" }
        val head = textValue.substring(0, offset)
        val tail = textValue.substring(offset)
        textValue = head
        val tailNode = TextNode(tail)
        parentNode()?.insertChildren(siblingIndex() + 1, listOf(tailNode))
        return tailNode
    }

    fun getWholeText(): String = textValue

    fun isBlank(): Boolean = textValue.isBlank()

    override fun text(): String = textValue

    override fun attr(attributeKey: String): String = super.attr(attributeKey)

    override fun outerHtml(): String = escapeHtml(textValue)

    companion object {
        fun createFromEncoded(encodedText: String): TextNode =
            TextNode(Parser.unescapeEntities(encodedText))
    }
}
