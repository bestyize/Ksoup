package xyz.thewind.ksoup.parser

import xyz.thewind.ksoup.nodes.Document
import xyz.thewind.ksoup.nodes.DocumentType
import xyz.thewind.ksoup.nodes.Comment
import xyz.thewind.ksoup.nodes.CDataNode
import xyz.thewind.ksoup.nodes.Element
import xyz.thewind.ksoup.nodes.Node
import xyz.thewind.ksoup.nodes.TextNode
import xyz.thewind.ksoup.nodes.XmlDeclaration
import xyz.thewind.ksoup.parser.Parser

internal object HtmlParser {
    private val voidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    private val headTags = setOf("base", "link", "meta", "script", "style", "title")
    private val headOnlyTags = setOf("base", "link", "meta", "title")
    private val rawTextTags = setOf("script", "style", "iframe", "noembed", "noframes", "xmp")
    private val eofRawTextTags = setOf("plaintext")
    private val rcDataTags = setOf("title", "textarea")
    private val tableSectionTags = setOf("tbody", "thead", "tfoot")
    private val tableContainerTags = setOf("table", "caption", "colgroup", "col", "tbody", "thead", "tfoot", "tr", "td", "th")
    private val tableScopedTags = setOf("table", "tbody", "thead", "tfoot", "tr")
    private val tableStructuralTags = setOf("caption", "colgroup", "col", "tbody", "thead", "tfoot", "tr", "td", "th")
    private val tableCellTags = setOf("td", "th", "caption")
    private val nonNestableFormattingTags = setOf("a", "button", "nobr")

    fun parse(html: String, baseUri: String): Document {
        val document = Document(baseUri).parser(Parser.htmlParser())
        val stack = mutableListOf<Element>()
        var index = 0

        parseLoop@ while (index < html.length) {
            val current = html[index]
            if (current != '<') {
                val nextTag = html.indexOf('<', startIndex = index).let { if (it == -1) html.length else it }
                appendTextNode(document, stack, MarkupParserSupport.decodeHtml(html.substring(index, nextTag)))
                index = nextTag
                continue
            }

            when {
                html.startsWith("<![CDATA[", index) -> {
                    val cdataEnd = html.indexOf("]]>", startIndex = index + 9)
                    val contentEnd = if (cdataEnd == -1) html.length else cdataEnd
                    appendNode(document, stack, CDataNode(html.substring(index + 9, contentEnd)))
                    index = if (cdataEnd == -1) html.length else cdataEnd + 3
                }

                html.startsWith("<!--", index) -> {
                    val commentEnd = html.indexOf("-->", startIndex = index + 4)
                    val contentEnd = if (commentEnd == -1) html.length else commentEnd
                    appendNode(document, stack, Comment(html.substring(index + 4, contentEnd)))
                    index = if (commentEnd == -1) html.length else commentEnd + 3
                }

                html.startsWithIgnoreCase("<!DOCTYPE", index) -> {
                    val declarationEnd = html.indexOf('>', startIndex = index + 2)
                    if (declarationEnd == -1) {
                        break
                    }
                    val declaration = html.substring(index + 2, declarationEnd)
                    document.insertBeforeDocumentElement(parseDocumentType(declaration))
                    index = declarationEnd + 1
                }

                html.startsWith("</", index) -> {
                    val closeEnd = html.indexOf('>', startIndex = index + 2)
                    if (closeEnd == -1) {
                        break
                    }
                    val tagName = html.substring(index + 2, closeEnd).trim().lowercase()
                    closeElement(stack, tagName)
                    index = closeEnd + 1
                }

                html.startsWith("<?", index) -> {
                    val declarationEnd = html.indexOf("?>", startIndex = index + 2).let {
                        if (it == -1) {
                            html.indexOf('>', startIndex = index + 2)
                        } else {
                            it
                        }
                    }
                    if (declarationEnd == -1) {
                        break
                    }
                    val declaration = parseXmlDeclaration(html.substring(index + 2, declarationEnd))
                    if (stack.isEmpty()) {
                        document.insertBeforeDocumentElement(declaration)
                    } else {
                        appendNode(document, stack, declaration)
                    }
                    index = declarationEnd + if (html.startsWith("?>", declarationEnd)) 2 else 1
                }

                html.startsWith("<!", index) -> {
                    index = html.indexOf('>', startIndex = index + 2).let { if (it == -1) html.length else it + 1 }
                }

                else -> {
                    val closeEnd = html.indexOf('>', startIndex = index + 1)
                    if (closeEnd == -1) {
                        appendTextNode(document, stack, MarkupParserSupport.decodeHtml(html.substring(index)))
                        break
                    }

                    val rawTag = html.substring(index + 1, closeEnd)
                    val parsed = MarkupParserSupport.parseStartTag(rawTag).let {
                        it.copy(name = it.name.lowercase())
                    }

                    when (parsed.name) {
                        "html" -> document.absorbHtmlAttributes(parsed.element)
                        "head" -> {
                            document.absorbHeadAttributes(parsed.element)
                            if (!isElementOpen(stack, "body") && stack.lastOrNull() !== document.head()) {
                                stack.removeAll { it === document.body() }
                                stack.add(document.head())
                            }
                        }

                        "body" -> {
                            document.absorbBodyAttributes(parsed.element)
                            stack.removeAll { it === document.head() }
                            if (!isElementOpen(stack, "body")) {
                                stack.add(document.body())
                            }
                        }

                        else -> {
                            if (parsed.name == "form" && isElementOpen(stack, "form")) {
                                index = closeEnd + 1
                                continue@parseLoop
                            }
                            closeNonNestableFormattingElement(stack, parsed.name)
                            if (shouldFosterParent(stack, parsed.name)) {
                                insertInFosterParent(document, stack, parsed.element)
                                if (!parsed.selfClosing && parsed.name !in voidTags) {
                                    stack.add(parsed.element)
                                }
                                index = closeEnd + 1
                                continue@parseLoop
                            }
                            autoCloseOptionalElements(stack, parsed.name)
                            val resolvedParent = resolveParentWithImpliedContainers(document, stack, parsed.name)
                            resolvedParent.appendChild(parsed.element)
                            if (!parsed.selfClosing && parsed.name !in voidTags) {
                                stack.add(parsed.element)
                                val specialContent = consumeSpecialTagContent(
                                    html = html,
                                    startIndex = closeEnd + 1,
                                    tagName = parsed.name
                                )
                                if (specialContent != null) {
                                    if (parsed.name in rawTextTags) {
                                        parsed.element.appendParsedText(specialContent.content)
                                    } else {
                                        parsed.element.appendParsedText(MarkupParserSupport.decodeHtml(specialContent.content))
                                    }
                                    closeElement(stack, parsed.name)
                                    index = specialContent.nextIndex
                                    continue@parseLoop
                                }
                            }
                        }
                    }

                    index = closeEnd + 1
                }
            }
        }

        return document
    }

    fun parseBodyFragment(bodyHtml: String, baseUri: String): Document {
        val document = Document(baseUri).parser(Parser.htmlParser())
        parse(bodyHtml, baseUri).body().childNodes().forEach { node ->
            document.body().appendChild(node)
        }
        return document
    }

    fun parseFragment(fragmentHtml: String, baseUri: String): List<Node> =
        parseBodyFragment(fragmentHtml, baseUri).body().childNodes().toList()

    private fun appendTextNode(document: Document, stack: List<Element>, text: String) {
        if (text.isBlank()) {
            return
        }

        if (shouldFosterParent(stack)) {
            insertTextInFosterParent(document, stack, text)
            return
        }

        val parent = when (val current = stack.lastOrNull()) {
            null -> document.body()
            document.documentElement() -> document.body()
            else -> current
        }
        parent.appendParsedText(text)
    }

    private fun appendNode(document: Document, stack: List<Element>, node: Node) {
        if (node !is Comment && shouldFosterParent(stack)) {
            insertInFosterParent(document, stack, node)
            return
        }
        val parent = when (val current = stack.lastOrNull()) {
            null -> document.body()
            document.documentElement() -> document.body()
            else -> current
        }
        parent.appendChild(node)
    }

    private fun closeElement(stack: MutableList<Element>, tagName: String) {
        for (i in stack.lastIndex downTo 0) {
            val element = stack[i]
            stack.removeAt(i)
            if (element.tagName().equals(tagName, ignoreCase = true)) {
                return
            }
        }
    }

    private fun autoCloseOptionalElements(stack: MutableList<Element>, incomingTag: String) {
        while (stack.isNotEmpty() && shouldAutoClose(stack.last().tagName().lowercase(), incomingTag)) {
            stack.removeAt(stack.lastIndex)
        }
    }

    private fun closeNonNestableFormattingElement(stack: MutableList<Element>, incomingTag: String) {
        if (incomingTag !in nonNestableFormattingTags) {
            return
        }
        if (isElementOpen(stack, incomingTag)) {
            closeElement(stack, incomingTag)
        }
    }

    private fun isElementOpen(stack: List<Element>, tagName: String): Boolean =
        stack.any { it.tagName().equals(tagName, ignoreCase = true) }

    private fun resolveParent(document: Document, stack: MutableList<Element>, tagName: String): Element {
        val current = stack.lastOrNull() ?: return when (tagName) {
            in headTags -> document.head()
            else -> document.body()
        }

        if (tagName in headOnlyTags) {
            return document.head()
        }

        if (current === document.head() && tagName !in headTags) {
            stack.removeAt(stack.lastIndex)
            return document.body()
        }

        if (current === document.documentElement()) {
            return if (tagName in headTags) document.head() else document.body()
        }

        return current
    }

    private fun resolveParentWithImpliedContainers(
        document: Document,
        stack: MutableList<Element>,
        tagName: String
    ): Element {
        var parent = resolveParent(document, stack, tagName)
        if (tagName in tableStructuralTags && parent.tagName().lowercase() !in tableContainerTags && !isElementOpen(stack, "table")) {
            parent = ensureImpliedContainer(parent, stack, "table")
        }
        when (tagName) {
            "frameset" -> {
                stack.removeAll { it === document.body() }
                parent = document.documentElement()
            }

            "frame" -> {
                if (!parent.tagName().equals("frameset", ignoreCase = true)) {
                    stack.removeAll { it === document.body() }
                    parent = if (isElementOpen(stack, "frameset")) {
                        stack.last { it.tagName().equals("frameset", ignoreCase = true) }
                    } else {
                        ensureImpliedContainer(document.documentElement(), stack, "frameset")
                    }
                }
            }

            "option", "optgroup" -> {
                while (stack.lastOrNull()?.tagName()?.equals("option", ignoreCase = true) == true) {
                    stack.removeAt(stack.lastIndex)
                }
                if (tagName == "optgroup" && stack.lastOrNull()?.tagName()?.equals("optgroup", ignoreCase = true) == true) {
                    stack.removeAt(stack.lastIndex)
                }
                parent = resolveParent(document, stack, tagName)
                if (!parent.tagName().equals("select", ignoreCase = true) &&
                    !parent.tagName().equals("optgroup", ignoreCase = true)
                ) {
                    parent = ensureImpliedContainer(parent, stack, "select")
                }
            }

            "li" -> {
                while (stack.lastOrNull()?.tagName()?.equals("li", ignoreCase = true) == true ||
                    stack.lastOrNull()?.tagName()?.equals("dt", ignoreCase = true) == true ||
                    stack.lastOrNull()?.tagName()?.equals("dd", ignoreCase = true) == true
                ) {
                    stack.removeAt(stack.lastIndex)
                }
                parent = resolveParent(document, stack, tagName)
                if (parent.tagName().equals("dl", ignoreCase = true)) {
                    stack.removeAt(stack.lastIndex)
                    parent = resolveParent(document, stack, tagName)
                }
                if (!parent.tagName().equals("ul", ignoreCase = true) &&
                    !parent.tagName().equals("ol", ignoreCase = true)
                ) {
                    parent = ensureImpliedContainer(parent, stack, "ul")
                }
            }

            "dt", "dd" -> {
                while (stack.lastOrNull()?.tagName()?.equals("li", ignoreCase = true) == true ||
                    stack.lastOrNull()?.tagName()?.equals("dt", ignoreCase = true) == true ||
                    stack.lastOrNull()?.tagName()?.equals("dd", ignoreCase = true) == true
                ) {
                    stack.removeAt(stack.lastIndex)
                }
                parent = resolveParent(document, stack, tagName)
                if (parent.tagName().equals("ul", ignoreCase = true) ||
                    parent.tagName().equals("ol", ignoreCase = true)
                ) {
                    stack.removeAt(stack.lastIndex)
                    parent = resolveParent(document, stack, tagName)
                }
                if (!parent.tagName().equals("dl", ignoreCase = true)) {
                    parent = ensureImpliedContainer(parent, stack, "dl")
                }
            }

            "tr" -> {
                if (parent.tagName().equals("table", ignoreCase = true)) {
                    parent = ensureImpliedContainer(parent, stack, "tbody")
                }
                if (parent.tagName().equals("colgroup", ignoreCase = true) ||
                    parent.tagName().equals("caption", ignoreCase = true)
                ) {
                    stack.removeAt(stack.lastIndex)
                    parent = resolveParentWithImpliedContainers(document, stack, tagName)
                }
            }

            "td", "th" -> {
                if (parent.tagName().equals("table", ignoreCase = true)) {
                    parent = ensureImpliedContainer(parent, stack, "tbody")
                }
                if (parent.tagName().equals("colgroup", ignoreCase = true) ||
                    parent.tagName().equals("caption", ignoreCase = true)
                ) {
                    stack.removeAt(stack.lastIndex)
                    parent = resolveParentWithImpliedContainers(document, stack, tagName)
                }
                if (parent.tagName().equals("tbody", ignoreCase = true) ||
                    parent.tagName().equals("thead", ignoreCase = true) ||
                    parent.tagName().equals("tfoot", ignoreCase = true)
                ) {
                    parent = ensureImpliedContainer(parent, stack, "tr")
                }
            }

            "col" -> {
                if (parent.tagName().equals("table", ignoreCase = true)) {
                    parent = ensureImpliedContainer(parent, stack, "colgroup")
                }
            }

            "tbody", "thead", "tfoot" -> {
                if (parent.tagName().equals("colgroup", ignoreCase = true) ||
                    parent.tagName().equals("caption", ignoreCase = true)
                ) {
                    stack.removeAt(stack.lastIndex)
                    parent = resolveParentWithImpliedContainers(document, stack, tagName)
                }
            }

            "caption" -> {
                while (stack.isNotEmpty()) {
                    val current = stack.last()
                    if (current.tagName().equals("table", ignoreCase = true)) {
                        parent = current
                        break
                    }
                    if (current.tagName().lowercase() in tableSectionTags ||
                        current.tagName().equals("tr", ignoreCase = true) ||
                        current.tagName().equals("td", ignoreCase = true) ||
                        current.tagName().equals("th", ignoreCase = true) ||
                        current.tagName().equals("colgroup", ignoreCase = true)
                    ) {
                        stack.removeAt(stack.lastIndex)
                        continue
                    }
                    break
                }
            }
        }
        return parent
    }

    private fun ensureImpliedContainer(parent: Element, stack: MutableList<Element>, tagName: String): Element {
        val container = Element(tagName)
        parent.appendChild(container)
        stack.add(container)
        return container
    }

    private fun parseDocumentType(rawDeclaration: String): DocumentType {
        val content = rawDeclaration.removePrefix("!").trim()
        val body = if (content.length >= 7 && content.substring(0, 7).equals("DOCTYPE", ignoreCase = true)) {
            content.substring(7).trim()
        } else {
            content
        }
        val tokens = MarkupParserSupport.tokenizeMarkup(body)
        val name = tokens.getOrNull(0).orEmpty()
        val pubSysKey = tokens.getOrNull(1)?.takeIf {
            it.equals("PUBLIC", ignoreCase = true) || it.equals("SYSTEM", ignoreCase = true)
        }?.uppercase()
        val publicId = when (pubSysKey) {
            "PUBLIC" -> tokens.getOrNull(2).orEmpty()
            else -> ""
        }
        val systemId = when (pubSysKey) {
            "PUBLIC" -> tokens.getOrNull(3).orEmpty()
            "SYSTEM" -> tokens.getOrNull(2).orEmpty()
            else -> ""
        }
        return DocumentType(name, publicId, systemId, pubSysKey)
    }

    private fun parseXmlDeclaration(rawDeclaration: String): XmlDeclaration {
        val parsed = MarkupParserSupport.parseStartTag(rawDeclaration.trim())
        val declaration = XmlDeclaration(parsed.name)
        parsed.element.attributes().asMap().forEach { (key, value) ->
            declaration.attr(key, value)
        }
        return declaration
    }

    private fun String.startsWithIgnoreCase(prefix: String, startIndex: Int): Boolean {
        if (startIndex < 0 || startIndex + prefix.length > length) {
            return false
        }
        return regionMatches(startIndex, prefix, 0, prefix.length, ignoreCase = true)
    }

    private fun shouldAutoClose(openTag: String, incomingTag: String): Boolean = when (openTag) {
        "p" -> incomingTag in setOf(
            "address", "article", "aside", "blockquote", "caption", "colgroup", "details", "dialog", "div",
            "dl", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "hr", "main", "menu", "nav", "ol", "p", "pre", "section", "summary", "table",
            "tbody", "td", "tfoot", "th", "thead", "tr", "ul"
        )
        "h1", "h2", "h3", "h4", "h5", "h6" -> incomingTag in setOf("h1", "h2", "h3", "h4", "h5", "h6")
        "li" -> incomingTag == "li"
        "dt" -> incomingTag == "dt" || incomingTag == "dd"
        "dd" -> incomingTag == "dt" || incomingTag == "dd"
        "option" -> incomingTag == "option" || incomingTag == "optgroup"
        "optgroup" -> incomingTag == "optgroup"
        "colgroup" -> incomingTag != "col"
        "caption" -> incomingTag in setOf("caption", "colgroup", "tbody", "thead", "tfoot", "tr")
        "thead" -> incomingTag in setOf("tbody", "tfoot", "caption", "colgroup")
        "tbody" -> incomingTag in setOf("tbody", "thead", "tfoot", "caption", "colgroup")
        "tfoot" -> incomingTag in setOf("tbody", "thead", "tfoot", "caption", "colgroup")
        "tr" -> incomingTag in setOf("tr", "tbody", "thead", "tfoot")
        "td" -> incomingTag in setOf("td", "th", "tr", "tbody", "thead", "tfoot")
        "th" -> incomingTag in setOf("td", "th", "tr", "tbody", "thead", "tfoot")
        else -> false
    }

    private fun shouldFosterParent(stack: List<Element>, incomingTag: String? = null): Boolean {
        val current = stack.lastOrNull() ?: return false
        val tableScopeIndex = stack.indexOfLast { it.tagName().lowercase() in tableScopedTags }
        if (tableScopeIndex == -1) {
            return false
        }
        val tableScope = stack[tableScopeIndex]
        if (current !== tableScope && !isDescendantOf(current, tableScope)) {
            return false
        }
        val openCell = stack.drop(tableScopeIndex + 1)
            .lastOrNull { candidate ->
                candidate.tagName().lowercase() in tableCellTags &&
                    (current === candidate || isDescendantOf(current, candidate))
            }
        if (openCell != null) {
            return false
        }
        if (incomingTag == null) {
            return true
        }
        return incomingTag !in tableStructuralTags
    }

    private fun isDescendantOf(node: Element, ancestor: Element): Boolean {
        var current = node.parent()
        while (current != null) {
            if (current === ancestor) {
                return true
            }
            current = current.parent()
        }
        return false
    }

    private fun insertTextInFosterParent(document: Document, stack: List<Element>, text: String) {
        val insertion = resolveFosterInsertion(document, stack)
        val textNode = TextNode(text)
        insertion.parent.insertChildren(insertion.index, listOf(textNode))
    }

    private fun insertInFosterParent(document: Document, stack: List<Element>, node: Node) {
        val insertion = resolveFosterInsertion(document, stack)
        insertion.parent.insertChildren(insertion.index, listOf(node))
    }

    private fun resolveFosterInsertion(document: Document, stack: List<Element>): FosterInsertion {
        val table = stack.lastOrNull { it.tagName().equals("table", ignoreCase = true) }
        val tableParent = table?.parent()
        if (table != null && tableParent != null) {
            return FosterInsertion(tableParent, table.siblingIndex())
        }
        return FosterInsertion(document.body(), document.body().childNodeSize())
    }

    private data class FosterInsertion(
        val parent: Element,
        val index: Int
    )

    private fun consumeSpecialTagContent(html: String, startIndex: Int, tagName: String): SpecialTagContent? {
        if (tagName !in rawTextTags && tagName !in rcDataTags && tagName !in eofRawTextTags) {
            return null
        }
        if (tagName in eofRawTextTags) {
            return SpecialTagContent(content = html.substring(startIndex), nextIndex = html.length)
        }
        val closeToken = "</$tagName"
        val closeStart = html.indexOfIgnoreCase(closeToken, startIndex)
        if (closeStart == -1) {
            val content = html.substring(startIndex)
            return SpecialTagContent(content = content, nextIndex = html.length)
        }
        val closeEnd = html.indexOf('>', closeStart + closeToken.length)
        if (closeEnd == -1) {
            val content = html.substring(startIndex)
            return SpecialTagContent(content = content, nextIndex = html.length)
        }
        return SpecialTagContent(
            content = html.substring(startIndex, closeStart),
            nextIndex = closeEnd + 1
        )
    }

    private fun String.indexOfIgnoreCase(needle: String, startIndex: Int): Int {
        if (needle.isEmpty()) {
            return startIndex.coerceIn(0, length)
        }
        for (index in startIndex.coerceAtLeast(0)..(length - needle.length).coerceAtLeast(-1)) {
            if (index < 0) {
                break
            }
            if (regionMatches(index, needle, 0, needle.length, ignoreCase = true)) {
                return index
            }
        }
        return -1
    }

    private data class SpecialTagContent(
        val content: String,
        val nextIndex: Int
    )
}
