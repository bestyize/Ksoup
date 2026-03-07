package xyz.thewind.ksoup.nodes

import xyz.thewind.ksoup.parser.Parser
import xyz.thewind.ksoup.select.NodeFilter
import xyz.thewind.ksoup.select.NodeTraversor
import xyz.thewind.ksoup.select.NodeVisitor

open class Node(private val nodeNameValue: String) {
    internal var parentNodeRef: Element? = null
    private var baseUriValue: String? = null

    open fun nodeName(): String = nodeNameValue

    open fun childNodes(): List<Node> = emptyList()

    fun childNode(index: Int): Node = childNodes()[index]

    open fun text(): String = ""

    open fun outerHtml(): String = ""

    open fun clone(): Node = when (this) {
        is Document -> Document(baseUri(), useShell).also { clone ->
            clone.outputSettings(outputSettings().clone())
            clone.parser(parser())
            childNodes().toList().forEach { child -> clone.appendChild(child.clone()) }
        }
        is Element -> createElementForTag(tagName(), attributes().copy()).also { clone ->
            childNodes().forEach { child -> clone.appendChild(child.clone()) }
        }
        is CDataNode -> CDataNode(getWholeText())
        is TextNode -> TextNode(getWholeText())
        is DataNode -> DataNode(getWholeData())
        is Comment -> Comment(getData())
        is DocumentType -> DocumentType(name(), publicId(), systemId(), pubSysKey())
        is XmlDeclaration -> XmlDeclaration(name(), attributes().copy(), isDeclaration())
        else -> Node(nodeName())
    }

    open fun shallowClone(): Node = when (this) {
        is Document -> Document(baseUri(), useShell).also { clone ->
            clone.outputSettings(outputSettings().clone())
            clone.parser(parser())
        }
        is Element -> createElementForTag(tagName(), attributes().copy())
        is CDataNode -> CDataNode(getWholeText())
        is TextNode -> TextNode(getWholeText())
        is DataNode -> DataNode(getWholeData())
        is Comment -> Comment(getData())
        is DocumentType -> DocumentType(name(), publicId(), systemId(), pubSysKey())
        is XmlDeclaration -> XmlDeclaration(name(), attributes().copy(), isDeclaration())
        else -> Node(nodeName())
    }

    open fun attr(attributeKey: String): String {
        if (attributeKey.startsWith("abs:")) {
            return absUrl(attributeKey.removePrefix("abs:"))
        }
        return ""
    }

    open fun attr(attributeKey: String, attributeValue: String): Node = this

    open fun hasAttr(attributeKey: String): Boolean {
        if (attributeKey.startsWith("abs:")) {
            val key = attributeKey.removePrefix("abs:")
            return hasAttr(key) && absUrl(key).isNotEmpty()
        }
        return false
    }

    open fun removeAttr(attributeKey: String): Node = this

    open fun attributesSize(): Int = 0

    open fun baseUri(): String = baseUriValue ?: parentNode()?.baseUri().orEmpty()

    open fun setBaseUri(baseUri: String): Node {
        baseUriValue = baseUri
        childNodes().forEach { it.setBaseUri(baseUri) }
        return this
    }

    fun parentNode(): Element? = parentNodeRef

    open fun parent(): Element? = parentNode()

    fun hasParent(): Boolean = parentNodeRef != null

    fun ownerDocument(): Document? {
        var current: Node? = this
        while (current != null) {
            if (current is Document) {
                return current
            }
            current = current.parentNode()
        }
        return null
    }

    open fun remove(): Node {
        parentNodeRef?.removeChild(this)
        return this
    }

    fun hasSameValue(other: Any?): Boolean =
        other is Node && nodeName() == other.nodeName() && outerHtml() == other.outerHtml()

    fun traverse(visitor: NodeVisitor): Node {
        NodeTraversor.traverse(visitor, this)
        return this
    }

    fun forEachNode(consumer: (Node) -> Unit): Node {
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                consumer(node)
            }

            override fun tail(node: Node, depth: Int) = Unit
        }, this)
        return this
    }

    fun filter(filter: NodeFilter): Node {
        NodeTraversor.filter(filter, this)
        return this
    }

    fun siblingNodes(): List<Node> = parentNode()?.childNodes()?.filter { it !== this }.orEmpty()

    fun root(): Node {
        var current: Node = this
        while (current.parentNode() != null) {
            current = current.parentNode()!!
        }
        return current
    }

    fun replaceWith(node: Node) {
        parentNode()?.replaceChild(this, node)
    }

    open fun unwrap(): Node? {
        val parent = parentNode() ?: return null
        val children = childNodes().toList()
        val index = siblingIndex()
        if (children.isNotEmpty()) {
            parent.insertChildren(index, children)
        }
        remove()
        return children.firstOrNull()
    }

    fun siblingIndex(): Int = parentNode()?.childNodes()?.indexOf(this) ?: 0

    fun previousSibling(): Node? = siblingAtOffset(-1)

    fun nextSibling(): Node? = siblingAtOffset(1)

    fun before(node: Node): Node {
        parentNode()?.insertChildren(siblingIndex(), listOf(node))
        return this
    }

    fun before(html: String): Node {
        val parser = ownerDocument()?.parser() ?: Parser.htmlParser()
        parentNode()?.insertChildren(siblingIndex(), parser.parseFragmentNodes(html, baseUri()))
        return this
    }

    fun after(node: Node): Node {
        parentNode()?.insertChildren(siblingIndex() + 1, listOf(node))
        return this
    }

    fun after(html: String): Node {
        val parser = ownerDocument()?.parser() ?: Parser.htmlParser()
        parentNode()?.insertChildren(siblingIndex() + 1, parser.parseFragmentNodes(html, baseUri()))
        return this
    }

    open fun wrap(html: String): Node {
        val parent = parentNode() ?: return this
        val parser = ownerDocument()?.parser() ?: Parser.htmlParser()
        val wrapNodes = parser.parseFragmentNodes(html, baseUri())
        if (wrapNodes.isEmpty()) {
            return this
        }

        val index = siblingIndex()
        parent.insertChildren(index, wrapNodes)
        val wrapElement = wrapNodes.firstNotNullOfOrNull { it as? Element } ?: return this
        var deepest = wrapElement
        while (deepest.childrenSize() == 1) {
            deepest = deepest.child(0)
        }

        parent.removeChild(this)
        deepest.appendChild(this)
        return this
    }

    fun absUrl(attributeKey: String): String {
        val value = attr(attributeKey).trim()
        if (value.isEmpty()) {
            return ""
        }
        if (looksAbsoluteUrl(value)) {
            return value
        }

        val base = baseUri().trim()
        if (base.isEmpty()) {
            return ""
        }

        return resolveRelativeUrl(base, value)
    }

    internal fun escapeHtml(value: String, inAttribute: Boolean = false): String = buildString {
        val settings = renderingSettings()
        val asciiOnly = settings.charset().equals("US-ASCII", ignoreCase = true)
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append(
                    when (settings.escapeMode()) {
                        Document.OutputSettings.EscapeMode.XHTML -> "&#x27;"
                        else -> "'"
                    }
                )
                '\u00A0' -> append(
                    when (settings.escapeMode()) {
                        Document.OutputSettings.EscapeMode.XHTML -> "&#xa0;"
                        else -> "&nbsp;"
                    }
                )
                else -> if (asciiOnly && ch.code > 0x7F) {
                    append("&#x")
                    append(ch.code.toString(16))
                    append(';')
                } else {
                    append(ch)
                }
            }
        }
    }

    internal fun renderingSettings(): Document.OutputSettings =
        if (this is Document) outputSettings() else ownerDocument()?.outputSettings() ?: Document.OutputSettings()

    private fun siblingAtOffset(offset: Int): Node? {
        val siblings = parentNode()?.childNodes() ?: return null
        val targetIndex = siblings.indexOf(this) + offset
        return siblings.getOrNull(targetIndex)
    }

    private fun looksAbsoluteUrl(value: String): Boolean {
        if (value.startsWith("//")) {
            return true
        }
        val schemeSeparator = value.indexOf(':')
        if (schemeSeparator <= 0) {
            return false
        }
        return value.substring(0, schemeSeparator).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    }

    private fun resolveRelativeUrl(base: String, relative: String): String {
        if (relative.startsWith("//")) {
            val schemeEnd = base.indexOf("://")
            val scheme = if (schemeEnd >= 0) base.substring(0, schemeEnd) else "https"
            return "$scheme:$relative"
        }

        val schemeEnd = base.indexOf("://")
        val authorityStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
        val pathStart = base.indexOf('/', authorityStart)
        val origin = if (pathStart >= 0) base.substring(0, pathStart) else base
        if (relative.startsWith('/')) {
            return origin + relative
        }

        val baseDir = when {
            base.endsWith("/") -> base.dropLast(1)
            pathStart == -1 -> base
            else -> base.substringBeforeLast('/')
        }
        return "$baseDir/$relative"
    }
}
