package xyz.thewind.ksoup.nodes

import xyz.thewind.ksoup.Connection
import xyz.thewind.ksoup.helper.HttpConnection
import xyz.thewind.ksoup.parser.Parser

class Document(
    private var baseUriValue: String = "",
    internal val useShell: Boolean = true
) : Element("#root") {
    class OutputSettings {
        enum class Syntax {
            HTML,
            XML
        }

        enum class EscapeMode {
            BASE,
            XHTML,
            EXTENDED
        }

        private var charsetValue: String = "UTF-8"
        private var prettyPrintValue: Boolean = true
        private var outlineValue: Boolean = false
        private var indentAmountValue: Int = 1
        private var maxPaddingWidthValue: Int = 30
        private var syntaxValue: Syntax = Syntax.HTML
        private var escapeModeValue: EscapeMode = EscapeMode.BASE

        fun charset(): String = charsetValue

        fun charset(charset: String): OutputSettings {
            charsetValue = charset
            return this
        }

        fun prettyPrint(): Boolean = prettyPrintValue

        fun prettyPrint(prettyPrint: Boolean): OutputSettings {
            prettyPrintValue = prettyPrint
            return this
        }

        fun outline(): Boolean = outlineValue

        fun outline(outline: Boolean): OutputSettings {
            outlineValue = outline
            return this
        }

        fun indentAmount(): Int = indentAmountValue

        fun indentAmount(indentAmount: Int): OutputSettings {
            indentAmountValue = indentAmount.coerceAtLeast(0)
            return this
        }

        fun maxPaddingWidth(): Int = maxPaddingWidthValue

        fun maxPaddingWidth(maxPaddingWidth: Int): OutputSettings {
            maxPaddingWidthValue = maxPaddingWidth.coerceAtLeast(0)
            return this
        }

        fun syntax(): Syntax = syntaxValue

        fun syntax(syntax: Syntax): OutputSettings {
            syntaxValue = syntax
            return this
        }

        fun escapeMode(): EscapeMode = escapeModeValue

        fun escapeMode(escapeMode: EscapeMode): OutputSettings {
            escapeModeValue = escapeMode
            return this
        }

        fun clone(): OutputSettings = OutputSettings()
            .charset(charsetValue)
            .prettyPrint(prettyPrintValue)
            .outline(outlineValue)
            .indentAmount(indentAmountValue)
            .maxPaddingWidth(maxPaddingWidthValue)
            .syntax(syntaxValue)
            .escapeMode(escapeModeValue)
    }

    private var htmlElement: Element? = null
    private var headElement: Element? = null
    private var bodyElement: Element? = null
    private var outputSettingsValue = OutputSettings()
    private var parserValue: Parser = if (useShell) Parser.htmlParser() else Parser.xmlParser()
    private var connectionValue: Connection? = null

    init {
        if (useShell) {
            ensureShell()
        }
    }

    fun location(): String = baseUriValue

    override fun baseUri(): String = baseUriValue

    override fun setBaseUri(baseUri: String): Document {
        baseUriValue = baseUri
        childNodes().forEach { it.setBaseUri(baseUri) }
        return this
    }

    fun documentElement(): Element = htmlElement
        ?: childNodes().filterIsInstance<Element>().firstOrNull()
        ?: ensureShell().first

    fun head(): Element = headElement ?: if (useShell) ensureShell().second else Element("head")

    fun body(): Element = bodyElement ?: if (useShell) ensureShell().third else Element("body")

    fun title(): String = headElement?.getElementsByTag("title")?.firstOrNull()?.text()
        ?: getElementsByTag("title").firstOrNull()?.text().orEmpty()

    fun title(title: String): Document {
        val head = head()
        val titleElement = head.getElementsByTag("title").firstOrNull() ?: Element("title").also {
            head.appendChild(it)
        }
        titleElement.replaceChildren(listOf(TextNode(title)))
        return this
    }

    fun forms(): List<FormElement> = getElementsByTag("form").filterIsInstance<FormElement>()

    fun expectForm(cssQuery: String): FormElement =
        selectFirst(cssQuery) as? FormElement
            ?: throw IllegalArgumentException("No form matched the query '$cssQuery'")

    fun createElement(tagName: String): Element = createElementForTag(tagName)

    fun charset(): String = outputSettingsValue.charset()

    fun charset(charset: String): Document {
        outputSettingsValue.charset(charset)
        return this
    }

    fun outputSettings(): OutputSettings = outputSettingsValue

    fun outputSettings(outputSettings: OutputSettings): Document {
        outputSettingsValue = outputSettings
        return this
    }

    fun parser(): Parser = parserValue

    fun parser(parser: Parser): Document {
        parserValue = parser
        if (parser.mode() == Parser.Mode.XML) {
            outputSettingsValue.syntax(OutputSettings.Syntax.XML)
        }
        return this
    }

    fun connection(): Connection {
        val existing = connectionValue
        if (existing != null) {
            return existing
        }
        val created = if (location().isNotEmpty()) {
            HttpConnection.connect(location())
        } else {
            HttpConnection.newSession()
        }
        connectionValue = created
        return created
    }

    fun connection(connection: Connection): Document {
        connectionValue = connection
        return this
    }

    fun normalise(): Document {
        if (!useShell) {
            return this
        }

        val (html, head, body) = ensureShell()

        childNodes().toList().forEach { node ->
            when {
                node === html -> Unit
                node is XmlDeclaration || node is DocumentType -> Unit
                else -> relocateNode(node, head, body, mergeShellElements = false)
            }
        }

        html.childNodes().toList().forEach { node ->
            when {
                node === head || node === body -> Unit
                else -> relocateNode(node, head, body, mergeShellElements = true)
            }
        }

        return this
    }

    override fun html(): String = buildString {
        renderNodeHtml(this@Document, this)
    }

    override fun outerHtml(): String = html()

    companion object {
        fun createShell(baseUri: String = ""): Document = Document(baseUri)
    }

    internal fun absorbHtmlAttributes(from: Element) {
        val html = htmlElement ?: return
        from.attributes().asMap().forEach { (key, value) ->
            html.attr(key, value)
        }
    }

    internal fun absorbHeadAttributes(from: Element) {
        val head = headElement ?: return
        from.attributes().asMap().forEach { (key, value) ->
            head.attr(key, value)
        }
    }

    internal fun absorbBodyAttributes(from: Element) {
        val body = bodyElement ?: return
        from.attributes().asMap().forEach { (key, value) ->
            body.attr(key, value)
        }
    }

    internal fun insertBeforeDocumentElement(node: Node) {
        val documentElementIndex = htmlElement?.let { childNodes().indexOf(it) }
            ?: childNodes().indexOfFirst { it is Element }.takeIf { it >= 0 }
            ?: childNodes().size
        insertChildren(documentElementIndex, listOf(node))
    }

    private fun ensureShell(): Triple<Element, Element, Element> {
        val html = htmlElement ?: Element("html").also {
            htmlElement = it
            appendChild(it)
        }
        val head = headElement ?: Element("head").also {
            headElement = it
            if (it.parent() == null) {
                html.prependChild(it)
            }
        }
        val body = bodyElement ?: Element("body").also {
            bodyElement = it
            if (it.parent() == null) {
                html.appendChild(it)
            }
        }
        return Triple(html, head, body)
    }

    private fun relocateNode(node: Node, head: Element, body: Element, mergeShellElements: Boolean) {
        when (node) {
            is Element -> when (node.tagName().lowercase()) {
                "html" -> if (mergeShellElements) {
                    absorbHtmlAttributes(node)
                    node.childNodes().toList().forEach { child -> relocateNode(child, head, body, mergeShellElements = true) }
                    node.remove()
                }
                "head" -> {
                    absorbHeadAttributes(node)
                    node.childNodes().toList().forEach { child -> relocateNode(child, head, body, mergeShellElements = false) }
                    node.remove()
                }
                "body" -> {
                    absorbBodyAttributes(node)
                    node.childNodes().toList().forEach { child -> body.appendChild(child) }
                    node.remove()
                }
                in setOf("base", "link", "meta", "script", "style", "title") -> {
                    node.remove()
                    head.appendChild(node)
                }
                else -> {
                    node.remove()
                    body.appendChild(node)
                }
            }

            else -> {
                node.remove()
                body.appendChild(node)
            }
        }
    }
}

internal fun Element.replaceChildren(nodes: List<Node>) {
    childNodes().toList().forEach { removeChild(it) }
    nodes.forEach { appendChild(it) }
}
